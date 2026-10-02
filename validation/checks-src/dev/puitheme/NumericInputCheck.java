package dev.puitheme;

public final class NumericInputCheck {
    private static int checks;
    private static void equal(float expected,float actual) {
        checks++;
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
            throw new AssertionError(expected+" != "+actual);
    }
    private static void rejected(String input,int decimals) {
        checks++;
        try { NumericInput.parse(input,decimals);throw new AssertionError("Accepted: "+input); }
        catch (NumberFormatException expected) { }
    }
    public static void main(String[] args) {
        equal(0f,NumericInput.parse("0",2));
        equal(0f,NumericInput.parse("-0",2));
        equal(0f,NumericInput.parse("0.000",2));
        equal(12.34f,NumericInput.parse("12.344",2));
        equal(12.35f,NumericInput.parse("12.345",2));
        equal(-12.34f,NumericInput.parse("-12.344",2));
        equal(-12.35f,NumericInput.parse("-12.345",2));
        equal(1.01f,NumericInput.parse("1.005",2));
        equal(-1.01f,NumericInput.parse("-1.005",2));
        equal(2.68f,NumericInput.parse("2.675",2));
        equal(1f,NumericInput.parse("1.004999999999999999999999999999",2));
        equal(1.01f,NumericInput.parse("1.005000000000000000000000000001",2));
        equal(-1f,NumericInput.parse("-1.004999999999999999999999999999",2));
        equal(-1.01f,NumericInput.parse("-1.005000000000000000000000000001",2));
        equal(2f,NumericInput.parse("1.5",0));
        equal(-2f,NumericInput.parse("-1.5",0));
        equal(1f,NumericInput.parse("1.499999999999999999999999999999",0));
        equal(-1f,NumericInput.parse("-1.499999999999999999999999999999",0));
        equal(1234.57f,NumericInput.parse("1.234565e3",2));
        equal(-1234.57f,NumericInput.parse("-1.234565E+3",2));
        equal(12.35f,NumericInput.parse(" \t12.345\r\n",2));
        equal(0f,NumericInput.parse("1E-2147483647",2));
        equal(0f,NumericInput.parse("-1E-2147483647",2));
        equal(0f,NumericInput.parse("1E-1000000000",2));
        equal(0f,NumericInput.parse("1E-100",2));
        equal(0f,NumericInput.parse("0E+2147483647",2));
        equal(Float.MAX_VALUE,NumericInput.parse("340282346638528859811704183484516925440",2));
        equal(-Float.MAX_VALUE,NumericInput.parse("-340282346638528859811704183484516925440",2));
        equal(Float.MAX_VALUE,NumericInput.parse(Float.toString(Float.MAX_VALUE),0));
        equal(Float.MIN_VALUE,NumericInput.parse(Float.toString(Float.MIN_VALUE),46));
        rejected(null,2);rejected("",2);rejected("  ",2);rejected("+",2);rejected("--1",2);
        rejected("1,2",2);rejected("12dp",2);rejected("NaN",2);rejected("Infinity",2);
        rejected("-Infinity",2);rejected("inf",2);rejected("1E+2147483647",2);
        rejected("1E+1000000000",2);rejected("1E2147483648",2);rejected("1E-2147483648",2);
        rejected("1E39",2);rejected("-1E39",2);rejected("9".repeat(257),2);
        rejected(" ".repeat(256)+"1",2);rejected("1",-1);rejected("1",Integer.MAX_VALUE);
        equal(1f,NumericInput.parse(" ".repeat(255)+"1",2));
        equal(1f,NumericInput.parse("1",256));
        equal(12.34567f,NumericInput.unrounded("12.34567"));
        equal(Float.MIN_VALUE,NumericInput.unrounded("1.4e-45"));
        for(String input:new String[]{"1e-100","NaN","1e39","-1e-100"}) {
            checks++;try{NumericInput.unrounded(input);throw new AssertionError("Accepted "+input);}catch(NumberFormatException expected){}
        }
        checks++;try{NumericInput.unrounded("12dp");throw new AssertionError("Accepted unit suffix");}
        catch(NumberFormatException expected){if(!"请输入有效的有限数值".equals(expected.getMessage()))throw new AssertionError(expected.getMessage());}
        checks++;try{NumericInput.parse("--1",2);throw new AssertionError("Accepted malformed sign");}
        catch(NumberFormatException expected){if(!"请输入有效的有限数值".equals(expected.getMessage()))throw new AssertionError(expected.getMessage());}
        System.out.println("NumericInputCheck passed: "+checks);
    }
}
