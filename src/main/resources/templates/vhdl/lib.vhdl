library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

package lib is

    -- A function to perform Fixed Point Multiplication between two numbers
    function FP_MULT(x: signed; y: signed)
            return signed;

    -- A function to perform Fixed Point Division between two numbers
    function FP_DIV(x: signed; y: signed)
            return signed;

end package lib;

package body lib is

    function FP_MULT(x: signed; y: signed)
            return signed is
    begin
        return RESIZE(SHIFT_RIGHT(x * y, x'length/2), x'length);
    end FP_MULT;

    function FP_DIV(x: signed; y: signed)
            return signed is
    begin
        return RESIZE(SHIFT_LEFT(x / y, x'length/2), x'length);
    end FP_DIV;

end package body lib;
