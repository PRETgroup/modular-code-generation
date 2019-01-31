library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

package lib is

    -- Generate a 16.16 Fixed Point number from a base 10 integer
    function CREATE_FP(x: integer)
            return signed;

    -- Generate a 16.16 Fixed Point number from a base 10 real number
    function CREATE_FP(x: real)
            return signed;

    -- A function to perform Fixed Point Multiplication between two numbers
    function FP_MULT(x: signed; y: signed)
            return signed;

    -- A function to perform Fixed Point Division between two numbers
    function FP_DIV(x: signed; y: signed)
            return signed;

end package lib;

package body lib is

    function CREATE_FP(x: integer)
            return signed is
    begin
        return to_signed(x * (2 ** 16), 32);
    end CREATE_FP;

    function CREATE_FP(x: real)
            return signed is
        variable presult : real;
    begin
        presult := x * (2.0**16);

        return to_signed(integer(presult), 32);
    end CREATE_FP;

    function FP_MULT(x: signed; y: signed)
            return signed is
    begin
        return RESIZE(SHIFT_RIGHT(x * y, x'length/2), x'length);
    end FP_MULT;

    function FP_DIV(x: signed; y: signed)
            return signed is
        variable xresize : signed(x'length + x'length/2 - 1 downto 0) := (others => '0');
    begin
        xresize(x'length + x'length/2 - 1 downto x'length/2) := x;

        return RESIZE(xresize / y, x'length);
    end FP_DIV;

--    function FP_DIV(x: signed; y: signed)
--            return signed is
--    begin
--        return RESIZE(SHIFT_LEFT(x / y, x'length/2), x'length);
--    end FP_DIV;

end package body lib;
