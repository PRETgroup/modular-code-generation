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

    -- A function to perform Integer base-2 log of a number
    function ILOG2(x: signed)
            return signed;

    -- A function to perform Fixed Point Natural Log of a number
    function FP_LOG(x: signed)
            return signed;

    -- A function to perform Fixed Point Floor
    function FP_FLOOR(x: signed)
            return signed;

    -- A function to perform Fixed Point Ceil
    function FP_CEIL(x: signed)
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

    function ILOG2(x: signed)
            return signed is
        variable xlog : signed(x'length downto 0) := (others => '0');
    begin
        for n in 0 to x'length loop
            if x >= SHIFT_LEFT(to_signed(1, x'length), n) then
                xlog := n;
            end if;
        end loop

        return xlog;
    end ILOG2;

    function FP_LOG(x: signed)
            return signed is
    begin
        return FP_MULT(CREATE_FP(0.69314718055995), SHIFT_LEFT(ILOG2(x), x'length/2)) - CREATE_FP(11.090354888959)
    end ILOG2;

    function FP_FLOOR(x: signed)
            return signed is
    begin
        return SHIFT_LEFT(SHIFT_RIGHT(x, x'length/2), x'length/2);
    end FP_DIV;

    function FP_CEIL(x: signed)
            return signed is
    begin
        if SHIFT_LEFT(x, x'length/2) > 0 then
            return SHIFT_LEFT(SHIFT_RIGHT(x, x'length/2) + 1, x'length/2);
        end if;

        return x;
    end FP_DIV;

--    function FP_DIV(x: signed; y: signed)
--            return signed is
--    begin
--        return RESIZE(SHIFT_LEFT(x / y, x'length/2), x'length);
--    end FP_DIV;

end package body lib;
