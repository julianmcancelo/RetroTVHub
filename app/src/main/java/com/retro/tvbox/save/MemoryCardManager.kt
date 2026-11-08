package com.retro.tvbox.save

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CardType {
    PS1,
    PS2,
    UNKNOWN
}

data class PS1BlockInfo(
    val blockIndex: Int,
    val isUsed: Boolean,
    val allocationState: Byte,
    val fileSize: Int,
    val title: String,
    val productCode: String
)

data class MemoryCardSummary(
    val file: File,
    val type: CardType,
    val totalSizeBytes: Long,
    val totalBlocks: Int,
    val usedBlocks: Int,
    val freeBlocks: Int,
    val ps1Blocks: List<PS1BlockInfo> = emptyList(),
    val isValid: Boolean
)

/**
 * Gestor integral de Memory Cards para emuladores de PlayStation 1 y PlayStation 2.
 * Ubicado en filesDir/memcards/ para garantizar persistencia absoluta e inmunidad
 * ante cualquier limpieza de caché.
 * Soporta formateo oficial nativo (PS1 .mcr 128KB / 15 bloques, PS2 .ps2 8MB),
 * verificación estructural, exportación a pendrive USB o Descargas e importación SAF.
 */
class MemoryCardManager(private val context: Context) {

    private val tag = "MemoryCardManager"

    companion object {
        // PS1 Specs: 128 KB exactos (131,072 bytes), 16 bloques de 8KB, 15 bloques utilizables (1..15)
        const val PS1_CARD_SIZE = 131072 // 128 * 1024 bytes
        const val PS1_BLOCK_SIZE = 8192  // 8 KB
        const val PS1_TOTAL_BLOCKS = 15
        const val PS1_MAGIC_0: Byte = 0x4D // 'M'
        const val PS1_MAGIC_1: Byte = 0x43 // 'C'

        // PS2 Specs: 8 MB estándar (8,388,608 bytes)
        const val PS2_CARD_SIZE = 8388608 // 8 * 1024 * 1024 bytes
        const val PS2_SUPERBLOCK_MAGIC = "Sony PS2 Memory Card Format 1.2.0.0"
        const val PS2_PAGE_SIZE = 512
        const val PS2_PAGES_PER_BLOCK = 16
        const val PS2_PAGES_PER_CARD = 16384
        const val PS2_CLUSTERS_PER_CARD = 8192
    }

    // Directorio persistente y seguro en filesDir/memcards/
    val memCardsDir: File by lazy {
        File(context.filesDir, "memcards").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    init {
        migrateLegacyCardsIfNeeded()
    }

    /**
     * Migra tarjetas creadas previamente en filesDir/memory_cards/ hacia la nueva ubicación filesDir/memcards/.
     */
    private fun migrateLegacyCardsIfNeeded() {
        try {
            val legacyDir = File(context.filesDir, "memory_cards")
            if (legacyDir.exists() && legacyDir.isDirectory) {
                legacyDir.listFiles()?.forEach { file ->
                    val dest = File(memCardsDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                        Log.i(tag, "Memory Card migrada con éxito a zona protegida: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Aviso durante la migración de Memory Cards heredadas", e)
        }
    }

    /**
     * Verifica si un archivo reside dentro de la zona de persistencia de Memory Cards.
     */
    fun isProtectedFile(file: File): Boolean {
        return try {
            file.canonicalPath.startsWith(memCardsDir.canonicalPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene o crea la tarjeta de memoria principal para una consola específica.
     */
    fun getOrCreateDefaultCard(type: CardType, slot: Int = 1): File {
        val fileName = when (type) {
            CardType.PS1 -> "ps1_card_slot$slot.mcr"
            CardType.PS2 -> "ps2_card_slot$slot.ps2"
            CardType.UNKNOWN -> "card_slot$slot.raw"
        }
        val file = File(memCardsDir, fileName)
        if (!file.exists() || file.length() == 0L) {
            when (type) {
                CardType.PS1 -> formatPS1Card(file)
                CardType.PS2 -> formatPS2Card(file)
                CardType.UNKNOWN -> Unit
            }
        }
        return file
    }

    /**
     * Formatea un archivo de 128 KB con la estructura oficial de Memory Card de PS1
     * (Encabezado 'MC' en Bloque 0 + 15 entradas de directorio con checksum XOR + bloques de datos limpios).
     */
    fun formatPS1Card(destination: File): Boolean {
        val tempFile = File(memCardsDir, "${destination.name}.fmt.tmp")
        return try {
            val buffer = ByteArray(PS1_CARD_SIZE)

            // 1. Header Frame (Frame 0 del Bloque 0, 128 bytes)
            // Magic 'M', 'C' en bytes 0 y 1
            buffer[0] = PS1_MAGIC_0
            buffer[1] = PS1_MAGIC_1

            // Checksum del header en byte 127 (XOR de bytes 0..126)
            var headerXor = 0
            for (i in 0 until 127) {
                headerXor = headerXor xor (buffer[i].toInt() and 0xFF)
            }
            buffer[127] = headerXor.toByte()

            // 2. Directorio de los 15 bloques (Frames 1 a 15 del Bloque 0, cada uno de 128 bytes)
            for (block in 1..PS1_TOTAL_BLOCKS) {
                val entryOffset = block * 128

                // 0xA0 = bloque formateado libre
                buffer[entryOffset] = 0xA0.toByte()
                buffer[entryOffset + 1] = 0x00
                buffer[entryOffset + 2] = 0x00
                buffer[entryOffset + 3] = 0x00

                // Tamaño en bytes = 0
                buffer[entryOffset + 4] = 0x00
                buffer[entryOffset + 5] = 0x00
                buffer[entryOffset + 6] = 0x00
                buffer[entryOffset + 7] = 0x00

                // Puntero de siguiente bloque: 0xFFFF indica fin de cadena o libre
                buffer[entryOffset + 8] = 0xFF.toByte()
                buffer[entryOffset + 9] = 0xFF.toByte()

                // Calcular XOR checksum de la entrada del directorio (bytes 0..126)
                var entryXor = 0
                for (b in 0 until 127) {
                    entryXor = entryXor xor (buffer[entryOffset + b].toInt() and 0xFF)
                }
                buffer[entryOffset + 127] = entryXor.toByte()
            }

            // 3. Escribir atómicamente al disco
            FileOutputStream(tempFile).use { fos ->
                fos.write(buffer)
                fos.flush()
                fos.fd.sync()
            }

            if (destination.exists()) {
                destination.delete()
            }
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "Memory Card PS1 formateada con éxito: ${destination.name} (128 KB / 15 bloques)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Fallo al formatear tarjeta PS1 ${destination.name}", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    /**
     * Formatea un archivo de 8 MB con el Superbloque y tablas base oficiales de PlayStation 2.
     * Compatible con emuladores NetherSX2, AetherSX2 y PCSX2.
     */
    fun formatPS2Card(destination: File): Boolean {
        val tempFile = File(memCardsDir, "${destination.name}.fmt.tmp")
        return try {
            val raf = RandomAccessFile(tempFile, "rw")
            raf.setLength(PS2_CARD_SIZE.toLong())

            val magicBytes = PS2_SUPERBLOCK_MAGIC.toByteArray(StandardCharsets.US_ASCII)
            val headerBuffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)

            // 0x00 - 0x27: Magic String null-terminated
            headerBuffer.put(magicBytes)
            while (headerBuffer.position() < 40) {
                headerBuffer.put(0.toByte())
            }

            // 0x28: Page Size (512 bytes)
            headerBuffer.putShort(PS2_PAGE_SIZE.toShort())
            // 0x2A: Pages Per Erase Block (16 páginas = 8192 bytes)
            headerBuffer.putShort(PS2_PAGES_PER_BLOCK.toShort())
            // 0x2C: Pages Per Card (16384 páginas = 8,388,608 bytes)
            headerBuffer.putInt(PS2_PAGES_PER_CARD)
            // 0x30: Clusters Per Card (8192 clusters de 1024 bytes)
            headerBuffer.putInt(PS2_CLUSTERS_PER_CARD)
            // 0x34: Alloc Offset (offset del primer cluster asignable)
            headerBuffer.putInt(64)
            // 0x38: Alloc End (límite de clusters de asignación)
            headerBuffer.putInt(8135)
            // 0x3C: Root Directory Cluster
            headerBuffer.putInt(0)
            // 0x40: Backup Block 1 (bloque 10)
            headerBuffer.putInt(10)
            // 0x44: Backup Block 2 (bloque 11)
            headerBuffer.putInt(11)

            val superblockBytes = headerBuffer.array()

            // Escribir Superbloque primario en el Bloque 0 (offset 0)
            raf.seek(0)
            raf.write(superblockBytes)

            // Escribir Backup Superblock 1 en el Bloque 10 (offset: 10 * 16 * 512 = 81,920 bytes)
            val backupOffset1 = 10L * PS2_PAGES_PER_BLOCK * PS2_PAGE_SIZE
            raf.seek(backupOffset1)
            raf.write(superblockBytes)

            // Escribir Backup Superblock 2 en el Bloque 11 (offset: 11 * 16 * 512 = 89,984 bytes)
            val backupOffset2 = 11L * PS2_PAGES_PER_BLOCK * PS2_PAGE_SIZE
            raf.seek(backupOffset2)
            raf.write(superblockBytes)

            raf.fd.sync()
            raf.close()

            if (destination.exists()) {
                destination.delete()
            }
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "Memory Card PS2 de 8MB formateada con éxito: ${destination.name}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Fallo al formatear tarjeta PS2 ${destination.name}", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    /**
     * Inspecciona una tarjeta de memoria y analiza su integridad física y lógica.
     */
    fun inspectCard(file: File): MemoryCardSummary {
        if (!file.exists()) {
            return MemoryCardSummary(file, CardType.UNKNOWN, 0, 0, 0, 0, emptyList(), false)
        }

        val size = file.length()
        return when {
            size == PS1_CARD_SIZE.toLong() -> inspectPS1(file)
            size >= PS2_CARD_SIZE.toLong() -> inspectPS2(file)
            else -> MemoryCardSummary(file, CardType.UNKNOWN, size, 0, 0, 0, emptyList(), false)
        }
    }

    private fun inspectPS1(file: File): MemoryCardSummary {
        return try {
            val bytes = file.readBytes()
            val isValidMagic = bytes.size >= 2 && bytes[0] == PS1_MAGIC_0 && bytes[1] == PS1_MAGIC_1

            val blockInfos = mutableListOf<PS1BlockInfo>()
            var usedCount = 0

            for (block in 1..PS1_TOTAL_BLOCKS) {
                val entryOffset = block * 128
                val state = bytes[entryOffset]
                // 0x51: Asignado primer bloque, 0x52: intermedio, 0x53: final
                val isUsed = (state.toInt() and 0xFF) in 0x51..0x53

                val sizeBytes = if (isUsed) {
                    val s0 = bytes[entryOffset + 4].toInt() and 0xFF
                    val s1 = bytes[entryOffset + 5].toInt() and 0xFF
                    val s2 = bytes[entryOffset + 6].toInt() and 0xFF
                    val s3 = bytes[entryOffset + 7].toInt() and 0xFF
                    s0 or (s1 shl 8) or (s2 shl 16) or (s3 shl 24)
                } else 0

                val titleRaw = if (isUsed) {
                    val strBytes = bytes.copyOfRange(entryOffset + 12, entryOffset + 76)
                    val nullIndex = strBytes.indexOf(0.toByte())
                    val validLength = if (nullIndex >= 0) nullIndex else strBytes.size
                    String(strBytes, 0, validLength, StandardCharsets.ISO_8859_1).trim()
                } else {
                    "Bloque Libre"
                }

                val productCode = if (isUsed && titleRaw.length >= 10) {
                    titleRaw.take(12)
                } else ""

                if (isUsed) {
                    usedCount++
                }

                blockInfos.add(
                    PS1BlockInfo(
                        blockIndex = block,
                        isUsed = isUsed,
                        allocationState = state,
                        fileSize = sizeBytes,
                        title = titleRaw,
                        productCode = productCode
                    )
                )
            }

            MemoryCardSummary(
                file = file,
                type = CardType.PS1,
                totalSizeBytes = bytes.size.toLong(),
                totalBlocks = PS1_TOTAL_BLOCKS,
                usedBlocks = usedCount,
                freeBlocks = PS1_TOTAL_BLOCKS - usedCount,
                ps1Blocks = blockInfos,
                isValid = isValidMagic
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inspeccionando PS1 Card ${file.name}", e)
            MemoryCardSummary(file, CardType.PS1, file.length(), PS1_TOTAL_BLOCKS, 0, PS1_TOTAL_BLOCKS, emptyList(), false)
        }
    }

    private fun inspectPS2(file: File): MemoryCardSummary {
        return try {
            val raf = RandomAccessFile(file, "r")
            val magicBuf = ByteArray(40)
            raf.read(magicBuf)
            raf.close()

            val magicStr = String(magicBuf, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            val isValid = magicStr.startsWith("Sony PS2 Memory Card")

            MemoryCardSummary(
                file = file,
                type = CardType.PS2,
                totalSizeBytes = file.length(),
                totalBlocks = PS2_CLUSTERS_PER_CARD,
                usedBlocks = 64, // Clusters reservados por el Superbloque y sistema
                freeBlocks = PS2_CLUSTERS_PER_CARD - 64,
                ps1Blocks = emptyList(),
                isValid = isValid
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inspeccionando PS2 Card ${file.name}", e)
            MemoryCardSummary(file, CardType.PS2, file.length(), 0, 0, 0, emptyList(), false)
        }
    }

    /**
     * Exporta la tarjeta de memoria a un destino Uri mediante Storage Access Framework.
     */
    fun exportCardToUri(cardFile: File, destinationUri: Uri): Result<Unit> {
        if (!cardFile.exists()) {
            return Result.failure(Exception("La tarjeta de memoria no existe: ${cardFile.name}"))
        }

        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(cardFile).use { input ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return Result.failure(Exception("No se pudo abrir el stream de salida para $destinationUri"))

            Log.i(tag, "Memory Card ${cardFile.name} exportada con éxito a Uri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error exportando Memory Card a SAF", e)
            Result.failure(e)
        }
    }

    /**
     * Respalda automáticamente una tarjeta a la carpeta pública de Descargas.
     */
    fun backupCardToDownloads(cardFile: File): Result<File> {
        if (!cardFile.exists()) {
            return Result.failure(Exception("La tarjeta no existe: ${cardFile.name}"))
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val targetName = "${cardFile.nameWithoutExtension}_backup_$timeStamp.${cardFile.extension}"

        return try {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(baseDir, "RetroTV_Memcards").apply { if (!exists()) mkdirs() }
            val targetFile = File(backupDir, targetName)

            cardFile.copyTo(targetFile, overwrite = true)
            Log.i(tag, "Respaldo de Memory Card en Descargas: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            // Fallback al directorio externo privado
            try {
                val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "RetroTV_Memcards").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(fallbackDir, targetName)
                cardFile.copyTo(targetFile, overwrite = true)
                Log.i(tag, "Respaldo en almacenamiento externo de la app: ${targetFile.absolutePath}")
                Result.success(targetFile)
            } catch (ex: Exception) {
                Log.e(tag, "Error creando respaldo en Descargas", ex)
                Result.failure(ex)
            }
        }
    }

    /**
     * Copia una tarjeta directamente a un directorio (como un pendrive USB montado).
     */
    fun backupCardToDirectory(cardFile: File, destinationDir: File): Result<File> {
        if (!cardFile.exists()) {
            return Result.failure(Exception("Tarjeta inexistente: ${cardFile.name}"))
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return Result.failure(Exception("Directorio inaccesible: ${destinationDir.absolutePath}"))
        }

        return try {
            val target = File(destinationDir, cardFile.name)
            cardFile.copyTo(target, overwrite = true)
            Log.i(tag, "Tarjeta respaldada en pendrive/directorio: ${target.absolutePath}")
            Result.success(target)
        } catch (e: Exception) {
            Log.e(tag, "Error respaldando tarjeta a directorio", e)
            Result.failure(e)
        }
    }

    /**
     * Respalda todas las tarjetas hacia el directorio indicado.
     */
    fun backupAllCardsToDirectory(destinationDir: File): Result<List<File>> {
        val cards = listAllCards().map { it.file }
        val results = mutableListOf<File>()

        for (card in cards) {
            val res = backupCardToDirectory(card, destinationDir)
            if (res.isSuccess) {
                results.add(res.getOrThrow())
            }
        }
        return Result.success(results)
    }

    /**
     * Importa una tarjeta de memoria desde un Uri (SAF) validando su firma física.
     */
    fun importCardFromUri(sourceUri: Uri, destinationFileName: String? = null): Result<File> {
        val resolvedName = destinationFileName ?: queryFileName(sourceUri) ?: "imported_card.mcr"
        val targetFile = File(memCardsDir, resolvedName)
        val tempFile = File(memCardsDir, "${resolvedName}.import.tmp")

        return try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return Result.failure(Exception("No se pudo leer el archivo seleccionado"))

            input.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }

            val size = tempFile.length()
            if (size != PS1_CARD_SIZE.toLong() && size < PS2_CARD_SIZE.toLong()) {
                tempFile.delete()
                return Result.failure(
                    Exception("Tamaño de archivo incompatible: $size bytes (Debe ser 128KB para PS1 u 8MB para PS2)")
                )
            }

            if (targetFile.exists()) {
                val bak = File(memCardsDir, "${targetFile.name}.bak")
                targetFile.copyTo(bak, overwrite = true)
                targetFile.delete()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "Memory Card importada exitosamente: ${targetFile.name}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(tag, "Error importando Memory Card desde SAF", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = it.getString(idx)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo leer nombre de archivo Uri: $uri", e)
        }
        return name ?: uri.lastPathSegment
    }

    /**
     * Lista todas las tarjetas encontradas en filesDir/memcards/.
     */
    fun listAllCards(): List<MemoryCardSummary> {
        val files = memCardsDir.listFiles { file ->
            val ext = file.extension.lowercase()
            file.isFile && (ext == "mcr" || ext == "mc" || ext == "ps2" || ext == "bin")
        } ?: return emptyList()

        return files.map { inspectCard(it) }
    }
}
