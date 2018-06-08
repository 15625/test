public class Test
{
    public static void main(String[] args)
    {
        if (args.length != 1)
            printUsage();

        try
        {
            var srcFile = args[0];
            var reader = new java.io.FileReader(srcFile);
            var srcReader = new java.io.LineNumberReader(reader);

            int indent = 0;
            var bindings = parseBindings(srcReader, indent);

            var env = new RecEnv(bindings, initEnv());

            var root = env.get("root");

            var result = root.evaluate();
            System.out.println(result);
        }
        catch (RootException ex)
        {
            System.err.println(ex);
            System.exit(-1);
        }
        catch (java.io.FileNotFoundException ex)
        {
            System.err.println(ex);
            System.exit(-1);
        }
    }

    private static void printUsage()
    {
        System.err.format("Usage: java %s <source_file>%n", Test.class.getName());
        System.exit(-1);
    }

    private static java.util.Map<String, Expr> parseBindings(
            java.io.LineNumberReader reader,
            int indent)
        throws ParseException
    {
        var bindings = new java.util.HashMap<String, Expr>();

        for (;;)
        {
            var b = parseBinding(reader, indent);
            if (b == null)
                break;

            if (bindings.containsKey(b.getKey()))
                throw new ParseException(
                        reader.getLineNumber(), 
                        String.format("already bound: %s%n", b.getKey()));

            bindings.put(b.getKey(), b.getValue());
        }

        return bindings;
    }

    private static java.util.Map.Entry<String, Expr> parseBinding(
            java.io.LineNumberReader reader,
            int indent)
        throws ParseException
    {
        String line;
        try
        {
            line = reader.readLine();
        }
        catch (java.io.IOException ex)
        {
            throw new ParseException(reader.getLineNumber(), "I/O error", ex);
        }

        if (line == null)
            return null;

        // indent
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ')
            i++;

        if (i < indent)
            return null;
        else if (i > indent)
            throw new ParseException(
                    reader.getLineNumber(), 
                    String.format("indent %d (expected %d)", i, indent));

        // variable
        var i0 = i;
        i = endOfNextToken(line, i0);
        var var = line.substring(i0, i);

        if (var.isEmpty())
            throw new ParseException(
                    reader.getLineNumber(), 
                    "variable name is empty");

        // =
        i0 = i;
        i = Math.min(line.length(), i0 + 3);
        var eq = line.substring(i0, i);
        if (!eq.equals(" = "))
            throw new ParseException(
                    reader.getLineNumber(),
                    String.format("bad equal sign: '%s' (expected ' = ')", eq));

        // expr
        var expr = parseExpr(line, i, reader, indent);

        return new java.util.AbstractMap.SimpleEntry<String, Expr>(var, expr);
    }

    private static Expr parseExpr(
            String line,
            int i0,
            java.io.LineNumberReader reader,
            int indent)
        throws ParseException
    {
        var i = endOfNextToken(line, i0);
        var t1 = line.substring(i0, i);

        if (i == line.length())
            return new VarExpr(t1);

        i0 = i + 1;
        i = endOfNextToken(line, i0);
        var t2 = line.substring(i0, i);

        if (i == line.length())
            throw new RuntimeException("NYI...");

        i0 = i + 1;
        i = endOfNextToken(line, i0);
        var t3 = line.substring(i0, i);

        if (i != line.length())
        {
            System.err.format("line %d: i0 %d i %d len %d%n", reader.getLineNumber(), i0, i, line.length());
            throw new RuntimeException("NYI...");
        }

        return new AppExpr(
                new AppExpr(new VarExpr(t1), new VarExpr(t2)),
                new VarExpr(t3));
    }

    private static int endOfNextToken(
            String line,
            int i0)
    {
        var i = i0;
        while (i < line.length() && line.charAt(i) != ' ')
            i++;
        return i;
    }

    private static Env initEnv()
    {
        return new SystemEnv();
    }
}

class RootException extends Exception
{
    public RootException(String msg)
    {
        super(msg);
    }

    public RootException(String msg, Throwable cause)
    {
        super(msg, cause);
    }
}

class ParseException extends RootException
{
    public ParseException(int line, String msg)
    {
        super(String.format("line %d: %s", line, msg));
    }

    public ParseException(int line, String msg, Throwable cause)
    {
        super(String.format("line %d: %s", line, msg), cause);
    }
}

class EvalException extends RootException
{
    // TODO: more context
    public EvalException(String msg)
    {
        super(msg);
    }

    public EvalException(String msg, Throwable cause)
    {
        super(msg, cause);
    }
}

interface Value
{
    Value apply(Value v)
        throws EvalException;
}

interface Expr
{
    public Value evaluate(Env env)
        throws EvalException;
}

interface Closure
{
    public Value evaluate()
        throws EvalException;
}

interface Env
{
    public Closure get(String var)
        throws EvalException;
}


class DoubleValue implements Value
{
    DoubleValue(double val)
    {
        m_val = val;
    }

    public String toString()
    {
        return String.valueOf(m_val);
    }

    public Value apply(Value v)
        throws EvalException
    {
        if (v instanceof BinOpValue)
        {
            return new DoubleOpValue(this, (BinOpValue)v);
        }
        else
            throw new EvalException(
                String.format("wrong arg type: %s %s", toString(), v.toString()));
    }

    public double val()
    {
        return m_val;
    }

    private final double m_val;
}

class BinOpValue implements Value
{
    public enum Op {
        Plus
    };

    BinOpValue(Op op)
    {
        m_op = op;
    }

    public String toString()
    {
        switch (m_op)
        {
            case Plus:
            return "+";

            default:
            throw new RuntimeException("unknown operator (should not happen)");
        }
    }

    public Value apply(Value v)
        throws EvalException
    {
        throw new EvalException(
                String.format("cannot apply: %s %s", toString(), v.toString()));
    }

    public java.util.function.DoubleBinaryOperator doubleOp()
    {
        switch (m_op)
        {
            case Plus:
            return (x, y) -> x + y;

            default:
            throw new RuntimeException("unknown operator (should not happen)");
        }
    }

    private final Op m_op;
}

class DoubleOpValue implements Value
{
    DoubleOpValue(DoubleValue lhs, BinOpValue op)
    {
        m_lhs = lhs;
        m_op = op;
    }

    public String toString()
    {
        return String.format("%s %s", m_lhs, m_op);
    }

    public Value apply(Value v)
        throws EvalException
    {
        if (v instanceof DoubleValue)
        {
            return new DoubleValue(
                    m_op.doubleOp().applyAsDouble(m_lhs.val(), ((DoubleValue)v).val()));
        }
        else
            throw new EvalException(
                String.format("wrong arg type: %s %s", toString(), v.toString()));
    }

    private final DoubleValue m_lhs;
    private final BinOpValue m_op;
}

class VarExpr implements Expr
{
    public VarExpr(String var)
    {
        m_var = var;
    }

    public Value evaluate(Env env)
        throws EvalException
    {
        var clo = env.get(m_var);
        return clo.evaluate();
    }

    private final String m_var;
}

class AppExpr implements Expr
{
    public AppExpr(Expr e1, Expr e2)
    {
        m_e1 = e1;
        m_e2 = e2;
    }

    public Value evaluate(Env env)
        throws EvalException
    {
        var v1 = m_e1.evaluate(env);
        var v2 = m_e2.evaluate(env);
        return v1.apply(v2);
    }

    private final Expr m_e1;
    private final Expr m_e2;
}

class ExprClosure implements Closure
{
    public ExprClosure(Expr expr, Env env)
    {
        m_expr = expr;
        m_env = env;
    }

    public Value evaluate()
        throws EvalException
    {
        // TODO: memoize result
        return m_expr.evaluate(m_env);
    }

    private final Expr m_expr;
    private final Env m_env;
}

class ValueClosure implements Closure
{
    public ValueClosure(Value val)
    {
        m_val = val;
    }

    public Value evaluate()
    {
        return m_val;
    }

    private final Value m_val;
}

class RecEnv implements Env
{
    public RecEnv(java.util.Map<String, Expr> bindings, Env outer)
    {
        m_bindings = new java.util.HashMap<String, Closure>();
        for (var b : bindings.entrySet())
            m_bindings.put(b.getKey(), new ExprClosure(b.getValue(), this));
        m_outer = outer;
    }

    public Closure get(String var)
        throws EvalException
    {
        var clo = m_bindings.get(var);
        if (clo == null)
            clo = m_outer.get(var);
        return clo;
    }

    private final java.util.Map<String, Closure> m_bindings;
    private final Env m_outer;
}

class SystemEnv implements Env
{
    public Closure get(String var)
        throws EvalException
    {
        var val = parseValue(var);
        return new ValueClosure(val);
    }

    private Value parseValue(String var)
        throws EvalException
    {
        switch (var)
        {
            case "+":
            return new BinOpValue(BinOpValue.Op.Plus);
        }

        try
        {
            return new DoubleValue(Double.valueOf(var));
        }
        catch (NumberFormatException ex)
        {
        }

        throw new EvalException(String.format("unbound var: %s", var));
    }
}
