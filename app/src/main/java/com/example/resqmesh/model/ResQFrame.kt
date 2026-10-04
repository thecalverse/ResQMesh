package com.example.resqmesh.model

import java.util.Locale
import kotlin.random.Random

/**
 * ResQMesh Frame Protocol Specifications:
 *
 * 11-Field Pipe-Delimited Mesh Standard:
 * `mtype|src_id|dest_id|fam_id|prio|lat|lon|text|ttl|seq_id|path`
 *
 * BLE Mobile Ingest Format (Phone -> Node over BLE RX):
 * `mtype|src_id|dest_id|fam_id|prio|lat|lon|text\n`
 */

enum class MessageType {
    ALERT,     // Life-safety SOS
    MSG,       // Field notes / chatter
    BEACON,    // Automated 30s heartbeat
    DISPATCH;  // Official command warnings

    companion object {
        fun fromString(str: String): MessageType {
            return try {
                valueOf(str.trim().uppercase(Locale.ROOT))
            } catch (e: Exception) {
                MSG
            }
        }
    }
}

enum class Priority {
    P1, // Life-threatening emergency / red alert
    P2, // Operational tactical update
    P3; // Safe status / routine telemetry

    companion object {
        fun fromString(str: String): Priority {
            return try {
                valueOf(str.trim().uppercase(Locale.ROOT))
            } catch (e: Exception) {
                P2
            }
        }
    }
}

data class ResQFrame(
    val mtype: MessageType = MessageType.MSG,
    val srcId: String = "RQ0000",
    val destId: String = "ALL",
    val famId: String = "NONE",
    val prio: Priority = Priority.P2,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val text: String = "",
    val ttl: Int = 4,
    val seqId: String = generateSeqId(),
    val rssi: Int = -100,
    val path: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val hopCount: Int
        get() = if (path.isNotBlank() && path.contains(">")) {
            path.split(">").size - 1
        } else {
            (4 - ttl).coerceAtLeast(0)
        }

    val routeDisplay: String
        get() = if (path.isNotBlank()) path.replace(">", " ➔ ") else srcId

    /**
     * Formats frame for Mobile -> ESP32 Node over BLE RX Characteristic
     * Format: `mtype|src_id|dest_id|fam_id|prio|lat|lon|text\n`
     */
    fun toBleIngestString(): String {
        return "${mtype.name}|$srcId|$destId|$famId|${prio.name}|%.6f|%.6f|$text\n".format(Locale.US, lat, lon)
    }

    /**
     * Formats full 11-field frame for Mesh broadcast
     * Format: `mtype|src_id|dest_id|fam_id|prio|lat|lon|text|ttl|seq_id|path`
     */
    fun toMeshString(): String {
        val currentPath = if (path.isBlank()) srcId else path
        val safeText = text.replace("|", " - ")
        return "${mtype.name}|$srcId|$destId|$famId|${prio.name}|%.6f|%.6f|$safeText|$ttl|$seqId|$currentPath".format(Locale.US, lat, lon)
    }

    companion object {
        fun generateSeqId(): String {
            val randomInt = Random.nextInt(0, 0xFFFF)
            val timeHex = (System.currentTimeMillis() % 0xFFFF).toString(16).uppercase(Locale.US)
            return "%04X_%s".format(Locale.US, randomInt, timeHex)
        }

        /**
         * Safely parses incoming strings from BLE or Mesh into ResQFrame.
         * Handles 11-field mesh standard, 10-field legacy, and 7-field BLE output formats.
         */
        fun parse(rawString: String, currentRssi: Int = -100): ResQFrame? {
            var trimmed = rawString.trim()
            if (trimmed.isEmpty()) return null

            // Strip transport and relay prefixes
            val prefixes = listOf("PC_INGEST:", "PC_SEND:", "RAW,", "RAW:", "TX:", "RX:", "INGEST:")
            for (prefix in prefixes) {
                if (trimmed.startsWith(prefix, ignoreCase = true)) {
                    trimmed = trimmed.substring(prefix.length).trim()
                }
            }

            // Dynamically strip any leading text or Mac/Node prefixes preceding message types
            val messageTypeTokens = listOf("MSG|", "ALERT|", "BEACON|", "DISPATCH|")
            for (token in messageTypeTokens) {
                val idx = trimmed.indexOf(token, ignoreCase = true)
                if (idx > 0) {
                    trimmed = trimmed.substring(idx).trim()
                    break
                }
            }

            val parts = trimmed.split("|")
            return try {
                when {
                    // 11-Field Mesh Frame: mtype|src_id|dest_id|fam_id|prio|lat|lon|text|ttl|seq_id|path
                    parts.size >= 11 -> {
                        val pathStr = if (parts.size > 11) parts.subList(10, parts.size).joinToString("|").trim() else parts[10].trim()
                        ResQFrame(
                            mtype = MessageType.fromString(parts[0]),
                            srcId = parts[1].trim(),
                            destId = parts[2].trim(),
                            famId = parts[3].trim(),
                            prio = Priority.fromString(parts[4]),
                            lat = parts[5].trim().toDoubleOrNull() ?: 0.0,
                            lon = parts[6].trim().toDoubleOrNull() ?: 0.0,
                            text = parts[7].trim(),
                            ttl = parts[8].trim().toIntOrNull() ?: 4,
                            seqId = parts[9].trim(),
                            rssi = currentRssi,
                            path = pathStr
                        )
                    }

                    // 10-Field Legacy Frame: mtype|src_id|dest_id|fam_id|prio|lat|lon|text|ttl|seq_id
                    parts.size == 10 -> {
                        val src = parts[1].trim()
                        ResQFrame(
                            mtype = MessageType.fromString(parts[0]),
                            srcId = src,
                            destId = parts[2].trim(),
                            famId = parts[3].trim(),
                            prio = Priority.fromString(parts[4]),
                            lat = parts[5].trim().toDoubleOrNull() ?: 0.0,
                            lon = parts[6].trim().toDoubleOrNull() ?: 0.0,
                            text = parts[7].trim(),
                            ttl = parts[8].trim().toIntOrNull() ?: 4,
                            seqId = parts[9].trim(),
                            rssi = currentRssi,
                            path = src
                        )
                    }

                    // 8 or 9 Field Frame: mtype|src_id|dest_id|fam_id|prio|lat|lon|text
                    parts.size in 8..9 -> {
                        val src = parts[1].trim()
                        ResQFrame(
                            mtype = MessageType.fromString(parts[0]),
                            srcId = src,
                            destId = parts[2].trim(),
                            famId = parts[3].trim(),
                            prio = Priority.fromString(parts[4]),
                            lat = parts[5].trim().toDoubleOrNull() ?: 0.0,
                            lon = parts[6].trim().toDoubleOrNull() ?: 0.0,
                            text = parts[7].trim(),
                            ttl = if (parts.size == 9) parts[8].trim().toIntOrNull() ?: 4 else 4,
                            seqId = generateSeqId(),
                            rssi = currentRssi,
                            path = src
                        )
                    }

                    // 7-Field BLE Output Format: mtype|src_id|prio|lat|lon|text|rssi
                    parts.size == 7 -> {
                        val parsedRssi = parts[6].trim().toIntOrNull() ?: currentRssi
                        val src = parts[1].trim()
                        ResQFrame(
                            mtype = MessageType.fromString(parts[0]),
                            srcId = src,
                            destId = "ALL",
                            famId = "NONE",
                            prio = Priority.fromString(parts[2]),
                            lat = parts[3].trim().toDoubleOrNull() ?: 0.0,
                            lon = parts[4].trim().toDoubleOrNull() ?: 0.0,
                            text = parts[5].trim(),
                            ttl = 4,
                            seqId = generateSeqId(),
                            rssi = parsedRssi,
                            path = src
                        )
                    }

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
