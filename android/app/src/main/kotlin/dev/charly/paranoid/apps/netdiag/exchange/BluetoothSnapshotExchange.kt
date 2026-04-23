package dev.charly.paranoid.apps.netdiag.exchange

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID

object BluetoothSnapshotExchange {

    private val SERVICE_UUID: UUID =
        UUID.fromString("a1b2c3d4-5678-9abc-def0-1234567890ab")

    private const val SERVICE_NAME = "paranoid-netdiag-exchange"
    private const val SERVER_ACCEPT_TIMEOUT_MS = 15_000L
    private const val MAX_PAYLOAD_BYTES = 1_000_000 // 1 MB

    private val json = Json { ignoreUnknownKeys = true }

    /** Check whether the device has a Bluetooth adapter and it is currently enabled. */
    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /** Check whether BLUETOOTH_CONNECT permission is granted (required on API 31+). */
    fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Return the runtime permissions the caller must request on API 31+. */
    fun requiredPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    }

    /**
     * Start a server that accepts one incoming RFCOMM connection, sends the
     * serialized [snapshot], and closes.
     *
     * The snapshot is written as a 4-byte big-endian length prefix followed by
     * the UTF-8 JSON payload.
     */
    @SuppressLint("MissingPermission")
    suspend fun startServer(
        context: Context,
        snapshot: DiagnosticsSnapshot,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var serverSocket: BluetoothServerSocket? = null
        var socket: BluetoothSocket? = null
        try {
            val adapter = adapter(context)
                ?: return@withContext Result.failure(IOException("Bluetooth not available"))

            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)

            socket = withTimeoutOrNull(SERVER_ACCEPT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { serverSocket.accept() }
            } ?: run {
                serverSocket.close()
                return@withContext Result.failure(IOException("No client connected within timeout"))
            }

            val bytes = json.encodeToString(DiagnosticsSnapshot.serializer(), snapshot)
                .toByteArray(Charsets.UTF_8)
            val lengthPrefix = ByteBuffer.allocate(4).putInt(bytes.size).array()

            socket.outputStream.write(lengthPrefix)
            socket.outputStream.write(bytes)
            socket.outputStream.flush()

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            socket?.close()
            serverSocket?.close()
        }
    }

    /**
     * Connect to a server running on [device], read the snapshot it sends, and
     * return the deserialized result.
     */
    @SuppressLint("MissingPermission")
    suspend fun startClient(
        context: Context,
        device: BluetoothDevice,
    ): Result<DiagnosticsSnapshot> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()

            val input = socket.inputStream
            val lengthBytes = input.readExactly(4)
            val length = ByteBuffer.wrap(lengthBytes).int

            if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
                return@withContext Result.failure(
                    IOException("Invalid payload length: $length"),
                )
            }

            val payload = input.readExactly(length)
            val jsonStr = String(payload, Charsets.UTF_8)
            val snapshot = json.decodeFromString<DiagnosticsSnapshot>(jsonStr)

            Result.success(snapshot)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: kotlinx.serialization.SerializationException) {
            Result.failure(IOException("Invalid snapshot format"))
        } finally {
            socket?.close()
        }
    }

    private fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.takeIf { it.isEnabled }
    }

    /** Read exactly [count] bytes from the stream or throw [IOException]. */
    private fun InputStream.readExactly(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read == -1) throw IOException("Connection closed before reading $count bytes")
            offset += read
        }
        return buffer
    }
}
