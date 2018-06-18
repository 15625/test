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
            catch (EnvException ex)
            {
                throw new EvalException(
                        ex.getMessage(),
                        new VarExpr("root", parser.srcLoc()));
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
        var l = reader.getLineNumber();
        if (l == 0)
            l = 1;
        return new SrcInfo(l, i0 + 1, i0 + 1);
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
        if (line == null || i0 == line.length())
        {
            try
            {
                line = reader.readLine();
                if (line == null)
                    return null;
            }
            catch (java.io.IOException ex)
            {
                throw new ParseException(
                        srcLoc(),
                        "I/O error", ex);
            }

            i0 = 0;
        }

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

        // variables
        i0 = i;

        var vars = new java.util.ArrayList<VarExpr>();
        for (;;)
        {
            i = endOfNextToken();
            var varName = line.substring(i0, i);

            if (varName.isEmpty())
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i + 1),
                        "variable name is empty");

            if (i == line.length())
            {
                i0 = i;
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i0 + 1),
                        "unexpected end of line");
            }

            if (!varName.equals("="))
                vars.add(new VarExpr(varName, new SrcInfo(l, i0 + 1, i)));

            i0 = i + 1;

            if (varName.equals("="))
                break;
        }

        if (vars.isEmpty())
            throw new ParseException(
                    new SrcInfo(l, i0 + 1, i0 + 1),
                    "missing variables");

        // expr
        var expr = parseExpr(indent);

        if (vars.size() == 1)
            return new java.util.AbstractMap.SimpleEntry<VarExpr, Expr>(
                    vars.get(0),
                    expr);
        else
        {
            var lambda = expr;
            for (int j = vars.size() - 1; j > 0; j--)
                lambda = new LambdaExpr(vars.get(j), lambda);
            return new java.util.AbstractMap.SimpleEntry<VarExpr, Expr>(
                    vars.get(0),
                    lambda);
        }
    }

    private Expr parseExpr(
            int indent)
        throws ParseException
    {
        Expr e = parseCondExpr();

        var bindings = parseBindings(indent + 4);

        return bindings.isEmpty() ? e : new LetrecExpr(e, bindings);
    }

    private Expr parseCondExpr()
        throws ParseException
    {
        var l = reader.getLineNumber();
        var i_start = i0;

        // true branch
        Expr et = null;

        boolean no_if = true;
        while (no_if)
        {
            var i = endOfNextToken();
            var t2 = line.substring(i0, i);

            if (t2.isEmpty())
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i + 1),
                        "expr term is empty");

            if (t2.equals("if"))
            {
                if (et == null)
                    throw new ParseException(
                            new SrcInfo(l, i0 + 1, i + 1),
                            "unexpected if");
                no_if = false;
            }
            else
            {
                var e2 = new VarExpr(t2, new SrcInfo(l, i0 + 1, i));
                et = et == null ? e2 : new AppExpr(et, e2);
            }

            if (i == line.length())
            {
                i0 = i;
                break;
            }

            i0 = i + 1;
        }

        if (no_if)
            return et;

        // condition
        Expr ec = null;
        for (;;)
        {
            var i = endOfNextToken();
            var t2 = line.substring(i0, i);

            if (t2.isEmpty())
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i + 1),
                        "expr term is empty");

            var e2 = new VarExpr(t2, new SrcInfo(l, i0 + 1, i));
            ec = ec == null ? e2 : new AppExpr(ec, e2);

            if (i == line.length())
            {
                i0 = i;
                break;
            }

            i0 = i + 1;
        }

        try
        {
            line = reader.readLine();
            if (line == null)
                throw new ParseException(
                        new SrcInfo(l, i0 + 1, i0 + 1),
                        "unexpected end of file");
        }
        catch (java.io.IOException ex)
        {
            throw new ParseException(
                    srcLoc(),
                    "I/O error", ex);
        }

        i0 = 0;
        l = reader.getLineNumber();

        // else indent
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ')
            i++;

        if (i != i_start)
            throw new ParseException(
                    new SrcInfo(l, 1, i),
                    String.format("indent %d (expected %d)", i, i_start));

        i0 = i;
        var ef = parseCondExpr();

        return new CondExpr(ec, et, ef);
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

abstract class RootException extends Exception
{
    public RootException(String msg)
    {
        super(msg);
    }

    public RootException(String msg, Throwable cause)
    {
        super(msg, cause);
    }

    public abstract SrcInfo srcInfo();

    public String toString()
    {
        return String.format("%s: %s: %s",
                getClass().getName(),
                srcInfo(),
                getMessage());
    }
}

class ParseException extends RootException
{
    public ParseException(SrcInfo srcInfo, String msg)
    {
        super(msg);
        m_srcInfo = srcInfo;
    }

    public ParseException(SrcInfo srcInfo, String msg, Throwable cause)
    {
        super(msg, cause);
        m_srcInfo = srcInfo;
    }

    public SrcInfo srcInfo()
    {
        return m_srcInfo;
    }

    private final SrcInfo m_srcInfo;
}

class EvalException extends RootException
{
    public EvalException(String msg)
    {
        super(msg);
    }

    public EvalException(String msg, Expr e)
    {
        super(msg);
        appendExprStack(e);
    }

    /*
    public EvalException(SrcInfo srcInfo, String msg, Throwable cause)
    {
        super(srcInfo, msg, cause);
    }
    */

    public SrcInfo srcInfo()
    {
        return m_expr_stack.get(0).srcInfo();
    }

    public void appendExprStack(Expr e)
    {
        m_expr_stack.add(e);
    }

    private final java.util.List<Expr> m_expr_stack =
        new java.util.ArrayList<Expr>();
}

class EnvException extends Exception
{
    public EnvException(String msg)
    {
        super(msg);
    }
}

interface Value
{
    boolean left();
    Value apply(Value v)
        throws EvalException;
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
        throws EnvException;
}

class SrcInfo
{
    // line/char positions start from 1
    SrcInfo(int line, int charBegin, int charEnd)
    {
        m_line = line;
        m_charBegin = charBegin;
        m_charEnd = charEnd;
    }

    public SrcInfo composeWith(SrcInfo that)
    {
        if (this.m_line != that.m_line)
            throw new RuntimeException(
                    String.format("line numbers mismatch: %d vs. %d",
                        this.m_line,
                        that.m_line));

        if (this.m_charEnd >= that.m_charBegin)
            throw new RuntimeException(
                    String.format("overlap: %d vs. %d",
                        this.m_charEnd,
                        that.m_charBegin));

        return new SrcInfo(this.m_line, this.m_charBegin, that.m_charEnd);
    }

    public String toString()
    {
        return String.format("%d(%d-%d)", m_line, m_charBegin, m_charEnd);
    }

    private final int m_line;
    private final int m_charBegin;
    private final int m_charEnd;
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

    public boolean left() { return false; }

    public Value apply(Value v)
        throws EvalException
    {
        throw new EvalException(
                String.format("cannot apply: %s %s", toString(), v.toString()));
    }

    public double val()
    {
        return m_val;
    }

    private final double m_val;
}

class BooleanValue implements Value
{
    BooleanValue(boolean val)
    {
        m_val = val;
    }

    public String toString()
    {
        return String.valueOf(m_val);
    }

    public boolean left() { return false; }

    public Value apply(Value v)
        throws EvalException
    {
        throw new EvalException(
                String.format("cannot apply: %s %s", toString(), v.toString()));
    }

    public boolean val()
    {
        return m_val;
    }

    private final boolean m_val;
}

class BinOpValue implements Value
{
    public enum Op {
        Plus,
        Minus,
        Equal
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

            case Minus:
            return "-";

            case Equal:
            return "=";

            default:
            throw new RuntimeException("unknown operator (should not happen)");
        }
    }

    public boolean left() { return true; }

    public Value apply(Value v)
        throws EvalException
    {
        if (v instanceof DoubleValue)
        {
            return new DoubleOpValue((DoubleValue)v, this);
        }
        else
            throw new EvalException(
                String.format("wrong arg type: %s %s", v.toString(), toString()));
    }

    public Op op()
    {
        return m_op;
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

    public boolean left() { return false; }

    public Value apply(Value v)
        throws EvalException
    {
        if (v instanceof DoubleValue)
        {
            switch (m_op.op())
            {
                case Plus:
                return new DoubleValue(m_lhs.val() + ((DoubleValue)v).val());

                case Minus:
                return new DoubleValue(m_lhs.val() - ((DoubleValue)v).val());

                case Equal:
                return new BooleanValue(m_lhs.val() == ((DoubleValue)v).val());

                default:
                throw new RuntimeException("unknown operator (should not happen)");
            }
        }
        else
            throw new EvalException(
                String.format("wrong arg type: %s %s", toString(), v.toString()));
    }

    private final DoubleValue m_lhs;
    private final BinOpValue m_op;
}

class LambdaValue implements Value
{
    LambdaValue(VarExpr var, Expr e, Env env)
    {
        m_var = var;
        m_e = e;
        m_env = env;
    }

    public String toString()
    {
        return String.format("\\%s.<...>", m_var.name());
    }

    public boolean left() { return false; }

    public Value apply(Value val)
        throws EvalException
    {
        var env1 = new ValueEnv(m_var.name(), val, m_env);
        return m_e.evaluate(env1);
    }

    private final VarExpr m_var;
    private final Expr m_e;
    private final Env m_env;
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

    // TODO: trace nicely
    // private static int trace_level = 0;
    public final Value evaluate(Env env)
        throws EvalException
    {
        // TODO: trace nicely
        // for (int i = 0; i < trace_level; i++)
        //     System.err.print(" ");
        // System.err.format("%s%n", srcInfo());
        // trace_level += 4;

        var val = _evaluate(env);

        // TODO: trace nicely
        // trace_level -= 4;
        // for (int i = 0; i < trace_level; i++)
        //     System.err.print(" ");
        // System.err.format("=> %s%n", val);

        return val;
    }

    abstract protected Value _evaluate(Env env)
        throws EvalException;

    private final SrcInfo m_srcInfo;
}

class VarExpr extends AbstractExpr
{
    public VarExpr(String var, SrcInfo srcInfo)
    {
        super(srcInfo);
        m_var = var;
    }

    protected Value _evaluate(Env env)
        throws EvalException
    {
        try
        {
            var clo = env.get(m_var);
            return clo.evaluate();
        }
        catch (EnvException ex)
        {
            throw new EvalException(ex.getMessage(), this);
        }
        catch (EvalException ex)
        {
            ex.appendExprStack(this);
            throw ex;
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
        super(e1.srcInfo().composeWith(e2.srcInfo()));
        m_e1 = e1;
        m_e2 = e2;
    }

    protected Value _evaluate(Env env)
        throws EvalException
    {
        var v1 = m_e1.evaluate(env);
        var v2 = m_e2.evaluate(env);
        try
        {
            if (v2.left())
                return v2.apply(v1);
            else if (!v1.left())
                return v1.apply(v2);
            else
                throw new EvalException(
                    String.format("cannot apply: %s %s", v1, v2));
        }
        catch (EvalException ex)
        {
            ex.appendExprStack(this);
            throw ex;
        }
    }

    private final Expr m_e1;
    private final Expr m_e2;
}

class LetrecExpr extends AbstractExpr
{
    public LetrecExpr(Expr e, java.util.Map<String, Expr> bindings)
    {
        super(e.srcInfo());
        m_e = e;
        m_bindings = bindings;
    }

    protected Value _evaluate(Env env)
        throws EvalException
    {
        var env1 = new RecEnv(m_bindings, env);

        return m_e.evaluate(env1);
    }

    private final Expr m_e;
    private final java.util.Map<String, Expr> m_bindings;
}

class LambdaExpr extends AbstractExpr
{
    public LambdaExpr(VarExpr var, Expr e)
    {
        super(var.srcInfo().composeWith(e.srcInfo()));
        m_var = var;
        m_e = e;
    }

    protected Value _evaluate(Env env)
        throws EvalException
    {
        return new LambdaValue(m_var, m_e, env);
    }

    private final VarExpr m_var;
    private final Expr m_e;
}

class CondExpr extends AbstractExpr
{
    public CondExpr(Expr ec, Expr et, Expr ef)
    {
        super(et.srcInfo().composeWith(ec.srcInfo()));
        m_ec = ec;
        m_et = et;
        m_ef = ef;
    }

    protected Value _evaluate(Env env)
        throws EvalException
    {
        var cond = m_ec.evaluate(env);
        if (! (cond instanceof BooleanValue))
            throw new EvalException(
                    String.format("condition not boolean: %s", cond),
                    this);
           
        if (((BooleanValue)cond).val())
            return m_et.evaluate(env);
        else
            return m_ef.evaluate(env);
    }

    private final Expr m_ec;
    private final Expr m_et;
    private final Expr m_ef;
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

class ValueEnv implements Env
{
    public ValueEnv(String v, Value val, Env outer)
    {
        m_v = v;
        m_val = val;
        m_outer = outer;
    }

    public Closure get(String var)
        throws EnvException
    {
        if (var.equals(m_v))
            return new ValueClosure(m_val);

        return m_outer.get(var);
    }

    private final String m_v;
    private final Value m_val;
    private final Env m_outer;
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
        throws EnvException
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
        throws EnvException
    {
        var val = parseValue(var);
        return new ValueClosure(val);
    }

    private Value parseValue(String var)
        throws EnvException
    {
        switch (var)
        {
            case "+":
            return new BinOpValue(BinOpValue.Op.Plus);

            case "-":
            return new BinOpValue(BinOpValue.Op.Minus);

            case "=":
            return new BinOpValue(BinOpValue.Op.Equal);
        }

        try
        {
            return new DoubleValue(Double.valueOf(var));
        }
        catch (NumberFormatException ex)
        {
        }

        throw new EnvException(String.format("unbound var: %s", var));
    }
}
