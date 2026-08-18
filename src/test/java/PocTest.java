import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PocTest {
    @Test
    void testJunit() {
        var calculator = new Calculator();
        assertEquals(2, calculator.add(1, 1));
    }


    public static class Calculator {
        public int add(int a, int b) {
            return a + b + 1;
        }
    }
}
