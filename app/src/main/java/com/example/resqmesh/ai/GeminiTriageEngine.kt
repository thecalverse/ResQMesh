package com.example.resqmesh.ai

import com.example.resqmesh.model.MessageType
import com.example.resqmesh.model.Priority
import com.example.resqmesh.model.ResQFrame
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TriageAnalysis(
    val summary: String,
    val severityLevel: String,
    val medicalGuidance: List<String>,
    val structuralHazards: List<String>,
    val immediateActions: List<String>
)

class GeminiTriageEngine(private val apiKey: String = "") {

    private val systemPrompt = """
        You are an expert Tactical Emergency Triage AI Advisor operating in an off-grid disaster response environment (ResQMesh System).
        Analyze the incoming emergency message, location, and priority. Provide structured, high-priority, life-saving field guidance.
        Keep your output direct, actionable, concise, and structured as follows:
        1. SEVERITY LEVEL: [CRITICAL / HIGH / MEDIUM / LOW]
        2. SITUATION SUMMARY: [Brief 1-sentence breakdown]
        3. IMMEDIATE TACTICAL ACTIONS: [Numbered list of 2-3 actions]
        4. MEDICAL & SURVIVAL GUIDANCE: [Numbered list of key medical procedures]
        5. STRUCTURAL & FIELD HAZARDS: [Key environmental risks to watch for]
    """.trimIndent()

    private var generativeModel: GenerativeModel? = if (apiKey.isNotBlank()) {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    } else null

    suspend fun analyzeFrame(frame: ResQFrame): TriageAnalysis = withContext(Dispatchers.IO) {
        val prompt = """
            $systemPrompt
            
            INCOMING DISASTER REPORT:
            - Source Node ID: ${frame.srcId}
            - Message Type: ${frame.mtype.name}
            - Priority: ${frame.prio.name}
            - GPS Coordinates: (${frame.lat}, ${frame.lon})
            - Payload / Report: "${frame.text}"
            - RSSI: ${frame.rssi} dBm
            
            Provide emergency tactical triage analysis.
        """.trimIndent()

        val model = generativeModel
        if (model != null && apiKey.isNotBlank()) {
            try {
                val response = model.generateContent(prompt)
                val text = response.text ?: ""
                if (text.isNotBlank()) {
                    return@withContext parseAiResponse(text)
                }
            } catch (e: Exception) {
                // Fall back to rule-based off-grid engine on error/offline
            }
        }

        return@withContext generateOffGridFallbackAnalysis(frame)
    }

    private fun parseAiResponse(rawText: String): TriageAnalysis {
        val lines = rawText.lines()
        var severity = "CRITICAL"
        var summary = "Disaster report analyzed."
        val actions = mutableListOf<String>()
        val medical = mutableListOf<String>()
        val hazards = mutableListOf<String>()

        var currentSection = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val upper = trimmed.uppercase(Locale.ROOT)
            when {
                upper.contains("SEVERITY LEVEL:") -> {
                    severity = trimmed.substringAfter(":").trim()
                }
                upper.contains("SITUATION SUMMARY:") -> {
                    summary = trimmed.substringAfter(":").trim()
                }
                upper.contains("IMMEDIATE TACTICAL ACTIONS:") -> currentSection = 1
                upper.contains("MEDICAL & SURVIVAL GUIDANCE:") -> currentSection = 2
                upper.contains("STRUCTURAL & FIELD HAZARDS:") -> currentSection = 3
                else -> {
                    when (currentSection) {
                        1 -> actions.add(trimmed.removePrefix("-").removePrefix("*").trim())
                        2 -> medical.add(trimmed.removePrefix("-").removePrefix("*").trim())
                        3 -> hazards.add(trimmed.removePrefix("-").removePrefix("*").trim())
                    }
                }
            }
        }

        if (actions.isEmpty()) actions.add("Maintain visual contact and deploy search team.")
        if (medical.isEmpty()) medical.add("Assess airway, breathing, circulation (ABCs) upon arrival.")
        if (hazards.isEmpty()) hazards.add("Check for unstable debris and downed power lines.")

        return TriageAnalysis(
            summary = summary,
            severityLevel = severity,
            medicalGuidance = medical,
            structuralHazards = hazards,
            immediateActions = actions
        )
    }

    private fun generateOffGridFallbackAnalysis(frame: ResQFrame): TriageAnalysis {
        val textUpper = frame.text.uppercase(Locale.ROOT)
        val isMedical = textUpper.contains("MEDICAL") || textUpper.contains("BLEED") || textUpper.contains("INJUR")
        val isTrapped = textUpper.contains("TRAPPED") || textUpper.contains("COLLAPSE") || textUpper.contains("DEBRIS")
        val isWater = textUpper.contains("WATER") || textUpper.contains("DEHYDRAT")

        val severity = when {
            frame.prio == Priority.P1 || frame.mtype == MessageType.ALERT -> "CRITICAL (RED)"
            frame.prio == Priority.P2 -> "HIGH (ORANGE)"
            else -> "ROUTINE (GREEN)"
        }

        val summary = "Off-grid triage for node ${frame.srcId}: [${frame.prio.name}] '${frame.text}' at (${frame.lat}, ${frame.lon})."

        val actions = mutableListOf<String>()
        val medical = mutableListOf<String>()
        val hazards = mutableListOf<String>()

        if (isTrapped) {
            actions.add("Dispatch structural search & rescue (USAR) team to (${frame.lat}, ${frame.lon}).")
            actions.add("Establish perimeter and silence heavy machinery to listen for tapping.")
            medical.add("Prepare crush syndrome treatment protocols and IV saline.")
            medical.add("Immobilize cervical spine before moving victim.")
            hazards.add("High secondary collapse risk from structural instability.")
            hazards.add("Risk of gas leaks and dust asphyxiation in void spaces.")
        } else if (isMedical) {
            actions.add("Deploy rapid tactical medical response team immediately.")
            actions.add("Ensure safe landing/evacuation zone near (${frame.lat}, ${frame.lon}).")
            medical.add("Apply direct pressure / tourniquet for severe hemorrhaging.")
            medical.add("Keep victim warm to prevent hypothermia and treat for shock.")
            hazards.add("Unsecured accident zone or hostile terrain.")
        } else if (isWater) {
            actions.add("Include clean drinking water and oral rehydration salts in air drop or supply route.")
            medical.add("Administer fluids slowly; monitor for severe heat stroke.")
            hazards.add("Contaminated local water sources following infrastructure failure.")
        } else {
            actions.add("Lock target node ${frame.srcId} and follow auto-routing compass vector.")
            actions.add("Establish two-way mesh communication over ResQMesh.")
            medical.add("Perform standard triage assessment (START protocol).")
            hazards.add("General disaster zone hazards: unstable ground, loss of communications.")
        }

        return TriageAnalysis(
            summary = summary,
            severityLevel = severity,
            medicalGuidance = medical,
            structuralHazards = hazards,
            immediateActions = actions
        )
    }
}
