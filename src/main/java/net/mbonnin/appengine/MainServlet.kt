package net.mbonnin.appengine

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class MainServlet : HttpServlet() {

    data class ScheduleSlotKt(
            val endDate: String = "",
            val sessionId: String = "",
            val roomId: String = "",
            val startDate: String = ""
    )

    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        System.out.println("service=${req.servletPath} pathInfo=${req.pathInfo}")


        resp.status = 200
        resp.contentType = "application/json"
        resp.writer.write(getScheduleSlots())
    }

    companion object {
        private val moshi = Moshi.Builder().build()!!

        private val listAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, ScheduleSlotKt::class.java)
            moshi.adapter<List<ScheduleSlotKt>>(type)
        }

        fun getScheduleSlots(): String {
            val options = FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setDatabaseUrl("https://android-makers-2019.firebaseio.com")
                    .build()

            FirebaseApp.initializeApp(options)
            val db = FirestoreClient.getFirestore()

            val result1 = db.collection("schedule").document("2019-04-23").get().get()
            val result2 = db.collection("schedule").document("2019-04-24").get().get()

            return listAdapter.toJson(convertResults(mapOf(
                    "2019-04-23" to result1,
                    "2019-04-24" to result2)))
        }

        private fun convertResults(results: Map<String, DocumentSnapshot>): List<ScheduleSlotKt> {
            val list = mutableListOf<ScheduleSlotKt>()
            for (result in results) {

                val day = result.value.data!!
                val timeSlots = day.getAsListOfMaps("timeslots")

                timeSlots.forEachIndexed { timeSlotIndex, timeSlot ->
                    val sessions = timeSlot.getAsListOfMaps("sessions")
                    sessions.forEachIndexed { index, session ->
                        val sessionId = session.getAsListOfStrings("items").firstOrNull()
                        if (sessionId != null) {
                            val startTime = timeSlot.getAsString("startTime")

                            val extend = (session.get("extend") as Long?)?.toInt()?.minus(1) ?: 0
                            val endTime = timeSlots[timeSlotIndex + extend].getAsString("endTime")
                            val roomId = when {
                                sessions.size == 1 -> "all"
                                index == 0 -> "moebius"
                                index == 1 -> "blin"
                                index == 2 -> "202"
                                index == 3 -> "204"
                                index == 4 -> "office"
                                else -> throw Exception("no room found")
                            }

                            list.add(ScheduleSlotKt(
                                    startDate = getDate(result.key, startTime),
                                    endDate = getDate(result.key, endTime),
                                    roomId = roomId,
                                    sessionId = sessionId
                            )
                            )
                        }
                    }
                }
            }
            return list
        }

        /**
         *
         * @param date the date as YYYY-MM-DD
         * @param time the time as HH:mm
         * @return a ISO86-01 String
         */
        private fun getDate(date: String, time: String): String {
            val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$date $time")

            val tz = TimeZone.getTimeZone("UTC")
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            df.timeZone = tz
            return df.format(d)
        }
    }
}

fun <K> Map<K, *>.getAsMap(k: K): Map<String, *> {
    return this.get(k) as Map<String, *>
}

fun <K> Map<K, *>.getAsListOfMaps(k: K): List<Map<String, *>> {
    return this.get(k) as List<Map<String, *>>
}

fun <K> Map<K, *>.getAsListOfStrings(k: K): List<String> {
    return this.get(k) as List<String>
}

fun <K> Map<K, *>.getAsString(k: K): String {
    return this.get(k) as String
}
