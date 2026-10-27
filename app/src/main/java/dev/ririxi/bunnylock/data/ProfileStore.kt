package dev.ririxi.bunnylock.data

import android.content.Context
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.ui.CellForm
import dev.ririxi.bunnylock.ui.LockProfile
import org.json.JSONArray
import org.json.JSONObject

object ProfileStore {
    private const val PREFS = "bunnylock"
    private const val KEY = "lockProfiles"

    fun load(context: Context): List<LockProfile> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val profiles = JSONArray(saved)
        return buildList(profiles.length()) {
            for (index in 0 until profiles.length()) {
                val json = profiles.getJSONObject(index)
                val bandsJson = json.getJSONObject("bands")
                val bands =
                    Group.entries.mapNotNull { group ->
                        if (!bandsJson.has(group.name)) return@mapNotNull null
                        val values = bandsJson.getJSONArray(group.name)
                        group to buildSet<Int> {
                            for (bandIndex in 0 until values.length()) {
                                add(values.getInt(bandIndex))
                            }
                        }
                    }.toMap()
                add(
                    LockProfile(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        bands = bands,
                        cell = json.optJSONObject("cell")?.toCellForm(),
                    ),
                )
            }
        }
    }

    fun save(context: Context, profiles: List<LockProfile>) {
        val json =
            JSONArray().apply {
                profiles.forEach { profile ->
                    put(
                        JSONObject()
                            .put("id", profile.id)
                            .put("name", profile.name)
                            .put(
                                "bands",
                                JSONObject().apply {
                                    profile.bands.forEach { (group, values) ->
                                        put(group.name, JSONArray(values.sorted()))
                                    }
                                },
                            ).put("cell", profile.cell?.toJson()),
                    )
                }
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
    }

    private fun JSONObject.toCellForm() =
        CellForm(
            rat = LockRat.valueOf(getString("rat")),
            lteEarfcn = optString("lteEarfcn"),
            ltePcis = optString("ltePcis"),
            nrArfcn = optString("nrArfcn"),
            nrPcis = optString("nrPcis"),
            nrScs = optInt("nrScs", 30),
            nrBand = optInt("nrBand", -1).takeIf { it >= 0 },
            nrScsPicked = optBoolean("nrScsPicked"),
        )

    private fun CellForm.toJson() =
        JSONObject()
            .put("rat", rat.name)
            .put("lteEarfcn", lteEarfcn)
            .put("ltePcis", ltePcis)
            .put("nrArfcn", nrArfcn)
            .put("nrPcis", nrPcis)
            .put("nrScs", nrScs)
            .put("nrBand", nrBand ?: -1)
            .put("nrScsPicked", nrScsPicked)
}
