public class Test
{
    public static void main(String[] args)
    {
        if (args.length != 1)
            printUsage();

        try
        {
            var srcFile = args[0];

            var parser = new Parser(srcFile);

            var indent = 0;
            var bindings = parser.parseBindings(indent);

            var env = new RecEnv(bindings, initEnv());

            Closure root;
            try
            {
                root = env.get("root");
            }
            catch (RawException ex)
            {
                throw new EvalException(parser.srcLoc(), ex.getMessage());
            }

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

    private static Env initEnv()
    {
        return new SystemEnv();
    }
}

class Parser
{
    public Parser(String srcFile)
        throws java.io.FileNotFoundException
    {
        var freader = new java.io.FileReader(srcFile);
        reader = new java.io.LineNumberReader(freader);

        line = null;
        i0 = 0;
    }

    public SrcInfo srcLoc()
    {
        return new SrcInfo(reader.getLineNumber(), i0, i0);
    }

    public java.util.Map<String, Expr> parseBindings(
            int indent)
        throws ParseException
    {
        var bindings = new java.util.HashMap<String, Expr>();

        for (;;)
        {
            var b = parseBinding(indent);
            if (b == null)
                break;

            if (bindings.containsKey(b.getKey()))
                throw new ParseException(
                        b.getKey().srcInfo(),
                        String.format("already bound: %s%n", b.getKey()));

            bindings.put(b.getKey().name(), b.getValue());
        }

        return bindings;
    }

    private java.util.Map.Entry<VarExpr, Expr> parseBinding(
            int indent)
        throws ParseException
    {
        if (line == null)
        {
            try
            {
                line = reader.readLine();
            }
            catch (java.io.IOException ex)
            {
                throw new ParseException(
                        new SrcInfo(reader.getLineNumber(), 0, 0),
                        "I/O error", ex);
            }

            i0 = 0;
        }

        if (line == null)
            return null;

        var l = reader.getLineNumber();

        if (i0 != 0)
            throw new RuntimeException(
                    String.format("line %d: i0 is not 0: %d (should not happend)", l, i0));

        // indent
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ')
            i++;

        if (i < indent)
            return null;
        else if (i > indent)
            throw new ParseException(
                    new SrcInfo(l, 1, i),
                    String.format("indent %d (expected %d)", i, indent));

        // variable
        i0 = i;
        i = endOfNextToken();
        var varName = line.substring(i0, i);

        if (varName.isEmpty())
            throw new ParseException(
                    new SrcInfo(l, i0 + 1, i + 1),
                    "variable name is empty");

        var var = new VarExpr(varName, new SrcInfo(l, i0 + 1, i));

        // =
        i0 = i;
        i = Math.min(line.length(), i0 + 3);
        var eq = line.substring(i0, i);
        if (!eq.equals(" = "))
            throw new ParseException(
                    new SrcInfo(l, i0 + 1, i),
                    String.format("bad equal sign: '%s' (expected ' = ')", eq));

        // expr
        i0 = i;
        var expr = parseExpr(indent);

        line = null;
        i0 = 0;

        return new java.util.AbstractMap.SimpleEntry<VarExpr, Expr>(var, expr);
    }

    private Expr parseExpr(
            int indent)
        throws ParseException
    {
        var l = reader.getLineNumber();

        Expr e = null;

        while (i0 <= line.length())
        {
            var i = endOfNextToken();
            var t2 = line.substring(i0, i);

            if (t2.isEmpty())
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i + 1),
                        "expr term is empty");

            var e2 = new VarExpr(t2, new SrcInfo(l, i0 + 1, i));

            e = e == null ? e2 : new AppExpr(e, e2);

            i0 = i + 1;
        }

        return e;
    }

    private int endOfNextToken()
    {
        var i = i0;
        while (i < line.length() && line.charAt(i) != ' ')
            i++;
        return i;
    }

    private java.io.LineNumberReader reader;
    String line;
    int i0;
}

class RootException extends Exception
{
    public RootException(SrcInfo srcInfo, String msg)
    {
        super(compMsg(srcInfo, msg));
    }

    public RootException(SrcInfo srcInfo, String msg, Throwable cause)
    {
        super(compMsg(srcInfo, msg), cause);
    }

    private static String compMsg(SrcInfo srcInfo, String msg)
    {
        return String.format("%d(%d-%d): %s", srcInfo.Line, srcInfo.CharBegin, srcInfo.CharEnd, msg);
    }
}

class ParseException extends RootException
{
    public ParseException(SrcInfo srcInfo, String msg)
    {
        super(srcInfo, msg);
    }

    public ParseException(SrcInfo srcInfo, String msg, Throwable cause)
    {
        super(srcInfo, msg, cause);
    }
}

class EvalException extends RootException
{
    // TODO: more context
    public EvalException(SrcInfo srcInfo, String msg)
    {
        super(srcInfo, msg);
    }

    public EvalException(SrcInfo srcInfo, String msg, Throwable cause)
    {
        super(srcInfo, msg, cause);
    }
}

class RawException extends Exception
{
    public RawException(String msg)
    {
        super(msg);
    }
}

interface Value
{
    Value apply(Value v)
        throws RawException;
}

interface Expr
{
    public Value evaluate(Env env)
        throws EvalException;

    // TODO: split out srcInfo
    public SrcInfo srcInfo();
}

interface Closure
{
    public Value evaluate()
        throws EvalException;
}

interface Env
{
    public Closure get(String var)
        throws RawException;
}

class SrcInfo
{
    SrcInfo(int line, int charBegin, int charEnd)
    {
        Line = line;
        CharBegin = charBegin;
        CharEnd = charEnd;
    }

    public final int Line;
    public final int CharBegin;
    public final int CharEnd;
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
        throws RawException
    {
        if (v instanceof BinOpValue)
        {
            return new DoubleOpValue(this, (BinOpValue)v);
        }
        else
            throw new RawException(
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
        throws RawException
    {
        throw new RawException(
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
        throws RawException
    {
        if (v instanceof DoubleValue)
        {
            return new DoubleValue(
                    m_op.doubleOp().applyAsDouble(m_lhs.val(), ((DoubleValue)v).val()));
        }
        else
            throw new RawException(
                String.format("wrong arg type: %s %s", toString(), v.toString()));
    }

    private final DoubleValue m_lhs;
    private final BinOpValue m_op;
}

abstract class AbstractExpr implements Expr
{
    public AbstractExpr(SrcInfo srcInfo)
    {
        m_srcInfo = srcInfo;
    }

    public SrcInfo srcInfo()
    {
        return m_srcInfo;
    }

    private final SrcInfo m_srcInfo;
}

class VarExpr extends AbstractExpr
{
    public VarExpr(String var, SrcInfo srcInfo)
    {
        super(srcInfo);
        m_var = var;
    }

    public Value evaluate(Env env)
        throws EvalException
    {
        try
        {
            var clo = env.get(m_var);
            return clo.evaluate();
        }
        catch (RawException ex)
        {
            throw new EvalException(srcInfo(), ex.getMessage());
        }
    }

    public String name()
    {
        return m_var;
    }

    private final String m_var;
}

class AppExpr extends AbstractExpr
{
    public AppExpr(Expr e1, Expr e2)
    {
        super(composeSrcInfo(e1.srcInfo(), e2.srcInfo()));
        m_e1 = e1;
        m_e2 = e2;
    }

    public Value evaluate(Env env)
        throws EvalException
    {
        var v1 = m_e1.evaluate(env);
        var v2 = m_e2.evaluate(env);
        try
        {
            return v1.apply(v2);
        }
        catch (RawException ex)
        {
            throw new EvalException(srcInfo() , ex.getMessage());
        }
    }

    private static SrcInfo composeSrcInfo(SrcInfo d1, SrcInfo d2)
    {
        if (d1.Line != d2.Line)
            throw new RuntimeException(
                    String.format("line numbers mismatch: %d vs. %d",
                        d1.Line,
                        d2.Line));

        return new SrcInfo(d1.Line, d1.CharBegin, d2.CharEnd);
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
        throws RawException
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
        throws RawException
    {
        var val = parseValue(var);
        return new ValueClosure(val);
    }

    private Value parseValue(String var)
        throws RawException
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

        throw new RawException(String.format("unbound var: %s", var));
    }
}
