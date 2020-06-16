package dojo.liftpasspricing;

import static spark.Spark.after;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.put;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import spark.Request;

public class Prices {

    public static Connection createApp() throws SQLException {

        final Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/lift_pass", "root", "mysql");

        port(4567);

        put("/prices", (req, res) -> {
            int liftPassCost = Integer.parseInt(req.queryParams("cost"));
            String liftPassType = req.queryParams("type");

            try (PreparedStatement stmt = connection.prepareStatement( //
                    "INSERT INTO base_price (type, cost) VALUES (?, ?) " + //
                    "ON DUPLICATE KEY UPDATE cost = ?")) {
                stmt.setString(1, liftPassType);
                stmt.setInt(2, liftPassCost);
                stmt.setInt(3, liftPassCost);
                stmt.execute();
            }

            return "";
        });

        get("/prices", (req, res) -> getPrice(connection, req));

        after((req, res) -> {
            res.type("application/json");
        });

        return connection;
    }

    private static String getPrice(Connection connection, Request req) throws SQLException, ParseException {
        String requestAge = req.queryParams("age");
        String requestType = req.queryParams("type");
        String requestDate = req.queryParams("date");
        return getPrice(connection, requestAge, requestType, requestDate);
    }

    public static String getPrice(Connection connection, String requestAge, String requestType, String requestDate) throws SQLException, ParseException {
        final Integer age = requestAge != null ? Integer.valueOf(requestAge) : null;

        try (PreparedStatement costStmt = connection.prepareStatement( //
                "SELECT cost FROM base_price " + //
                "WHERE type = ?")) {
            costStmt.setString(1, requestType);
            try (ResultSet result = costStmt.executeQuery()) {
                result.next();

                int reduction;
                boolean isHoliday = false;

                if (age != null && age < 6) {
                    return "{ \"cost\": 0}";
                } else {
                    reduction = 0;

                    if (!requestType.equals("night")) {
                        DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");

                        try (PreparedStatement holidayStmt = connection.prepareStatement( //
                                "SELECT * FROM holidays")) {
                            try (ResultSet holidays = holidayStmt.executeQuery()) {

                                while (holidays.next()) {
                                    Date holiday = holidays.getDate("holiday");
                                    if (requestDate != null) {
                                        Date d = isoFormat.parse(requestDate);
                                        if (d.getYear() == holiday.getYear() && //
                                            d.getMonth() == holiday.getMonth() && //
                                            d.getDate() == holiday.getDate()) {
                                            isHoliday = true;
                                        }
                                    }
                                }

                            }
                        }

                        if (requestDate != null) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTime(isoFormat.parse(requestDate));
                            if (!isHoliday && calendar.get(Calendar.DAY_OF_WEEK) == 2) {
                                reduction = 35;
                            }
                        }

                        // TODO apply reduction for others
                        if (age != null && age < 15) {
                            return "{ \"cost\": " + (int) Math.ceil(result.getInt("cost") * .7) + "}";
                        } else {
                            if (age == null) {
                                double cost = result.getInt("cost") * (1 - reduction / 100.0);
                                return "{ \"cost\": " + (int) Math.ceil(cost) + "}";
                            } else {
                                if (age > 64) {
                                    double cost = result.getInt("cost") * .75 * (1 - reduction / 100.0);
                                    return "{ \"cost\": " + (int) Math.ceil(cost) + "}";
                                } else {
                                    double cost = result.getInt("cost") * (1 - reduction / 100.0);
                                    return "{ \"cost\": " + (int) Math.ceil(cost) + "}";
                                }
                            }
                        }
                    } else {
                        if (age != null && age >= 6) {
                            if (age > 64) {
                                return "{ \"cost\": " + (int) Math.ceil(result.getInt("cost") * .4) + "}";
                            } else {
                                return "{ \"cost\": " + result.getInt("cost") + "}";
                            }
                        } else {
                            return "{ \"cost\": 0}";
                        }
                    }
                }
            }
        }
    }

}
