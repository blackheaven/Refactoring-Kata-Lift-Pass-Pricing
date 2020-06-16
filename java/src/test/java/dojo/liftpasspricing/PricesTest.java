package dojo.liftpasspricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import spark.Spark;

public class PricesTest {

    private Connection connection;

    @BeforeEach
    public void createPrices() throws SQLException {
        connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/lift_pass", "root", "mysql");
//        connection = Prices.createApp();
    }

    @AfterEach
    public void stopApplication() throws SQLException {
//        Spark.stop();
        connection.close();
    }

//    @Test
//    public void doesSomething() {
//        JsonPath response = RestAssured.
//            given().
//                port(4567).
//            when().
//                // construct some proper url parameters
//                get("/prices?type=night&age=23&date=2019-02-18").
//
//            then().
//                assertThat().
//                    statusCode(200).
//                assertThat().
//                    contentType("application/json").
//            extract().jsonPath();
//
//        assertEquals(Integer.valueOf(19), response.get("cost"));
//    }

    @ParameterizedTest
    @MethodSource
    public void testGetPrice(String requestAge, String requestType, String requestDate, String output) throws Exception {
        assertEquals(output, Prices.getPrice(connection, requestAge, requestType, requestDate));
    }

    private static Stream<Arguments> testGetPrice() {
        return Stream.of(
                Arguments.of(null, "night", "2019-02-18", "{ \"cost\": 0}"),
                Arguments.of("23", "night", "2019-02-18", "{ \"cost\": 19}"),
                Arguments.of("23", "1jour", "2019-02-18", "{ \"cost\": 35}")
        );
    }

    @Test
    public void testGetPriceGolden() throws Exception {
        List<String> ages = IntStream.range(1, 76).mapToObj(Integer::toString).collect(Collectors.toList());
        ages.add(null);
        List<String> types = Arrays.asList("night", "1jour");
        List<String> dates = LongStream.range(0, 365).mapToObj(increment ->
                LocalDate.of(2019, 1, 1)
                        .plusDays(increment)
                        .format(DateTimeFormatter.ofPattern("YYYY-MM-DD")))
                .collect(Collectors.toList());

        List<String> outputs = new ArrayList<>();
        for (String age : ages) {
            for (String date : dates) {
                for (String type : types) {
                    outputs.add(Prices.getPrice(connection, age, type, date));
                }
            }
        }
        FileWriter fileWriter = new FileWriter("ranTest.txt");
        for (String output : outputs) {
            fileWriter.write(output + "\n");
        }
        fileWriter.close();
        List<String> expected = Files.lines(Paths.get("goldenMaster.txt")).collect(Collectors.toList());

        assertEquals(expected, outputs);
    }
}
