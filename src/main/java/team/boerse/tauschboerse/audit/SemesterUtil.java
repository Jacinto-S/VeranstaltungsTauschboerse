package team.boerse.tauschboerse.audit;

import java.util.Calendar;
import java.util.Date;

public class SemesterUtil {

    public static String getCurrentSemester() {
        return getSemesterForDate(new Date());
    }

    public static String getSemesterForDate(Date date) {
        if (date == null)
            return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        // Two-week buffer in milliseconds
        final long BUFFER_MS = 1000L * 60 * 60 * 24 * 14;

        int year = cal.get(Calendar.YEAR);

        Calendar ssStart = Calendar.getInstance();
        ssStart.clear();
        ssStart.set(Calendar.YEAR, year);
        ssStart.set(Calendar.MONTH, Calendar.APRIL - 1);
        ssStart.set(Calendar.DAY_OF_MONTH, 1);

        Calendar wsStart = Calendar.getInstance();
        wsStart.clear();
        wsStart.set(Calendar.YEAR, year);
        wsStart.set(Calendar.MONTH, Calendar.OCTOBER - 1);
        wsStart.set(Calendar.DAY_OF_MONTH, 1);

        long ts = date.getTime();

        if (ssStart.getTimeInMillis() - ts > 0 && ssStart.getTimeInMillis() - ts <= BUFFER_MS) {
            return "SS" + year;
        }

        if (wsStart.getTimeInMillis() - ts > 0 && wsStart.getTimeInMillis() - ts <= BUFFER_MS) {
            return "WS" + year;
        }

        int month = cal.get(Calendar.MONTH) + 1;

        if (month >= 10) {
            return "WS" + year;
        }

        if (month >= 4 && month <= 9) {
            return "SS" + year;
        }

        return "WS" + (year - 1);
    }

}
