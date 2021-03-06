package jscl.math.function;

import com.google.common.collect.Lists;
import jscl.CustomFunctionCalculationException;
import jscl.JsclMathEngine;
import jscl.NumeralBase;
import jscl.math.*;
import jscl.text.ParseException;
import jscl.text.msg.JsclMessage;
import jscl.text.msg.Messages;
import org.solovyev.common.math.MathEntity;
import org.solovyev.common.msg.MessageType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomFunction extends Function implements IFunction {

    private final static AtomicInteger counter = new AtomicInteger(0);

    private final int id;
    @Nonnull
    private Expression content;
    @Nullable
    private String description;
    @Nonnull
    private List<String> parameterNames = Collections.emptyList();
    @Nullable
    private List<ConstantData> parameterConstants;

    private CustomFunction(@Nonnull String name,
                           @Nonnull List<String> parameterNames,
                           @Nonnull Expression content,
                           @Nullable String description) {
        super(name, new Generic[parameterNames.size()]);
        this.parameterNames = parameterNames;
        this.content = content;
        this.description = description;
        this.id = counter.incrementAndGet();
    }

    @Nonnull
    private List<ConstantData> makeParameterConstants(@Nonnull List<String> names) {
        return new ArrayList<>(Lists.transform(names, new com.google.common.base.Function<String, ConstantData>() {
            @Nullable
            @Override
            public ConstantData apply(@Nullable String name) {
                return new ConstantData(name);
            }
        }));
    }

    private CustomFunction(@Nonnull String name,
                           @Nonnull List<String> parameterNames,
                           @Nonnull String content,
                           @Nullable String description) throws CustomFunctionCalculationException {
        super(name, new Generic[parameterNames.size()]);
        this.parameterNames = parameterNames;
        final JsclMathEngine engine = JsclMathEngine.getInstance();
        final NumeralBase nb = engine.getNumeralBase();
        if (nb != NumeralBase.dec) {
            // numbers in functions are only supported in decimal base
            engine.setNumeralBase(NumeralBase.dec);
        }
        try {
            this.content = Expression.valueOf(content);
            ensureNoImplicitFunctions();
        } catch (ParseException e) {
            throw new CustomFunctionCalculationException(this, e);
        } finally {
            if (nb != NumeralBase.dec) {
                engine.setNumeralBase(nb);
            }
        }
        this.description = description;
        this.id = counter.incrementAndGet();
    }

    private void ensureNoImplicitFunctions() {
        for (int i = 0; i < this.content.size(); i++) {
            final Literal literal = this.content.literal(i);
            for (int j = 0; j < literal.size(); j++) {
                final Variable variable = literal.getVariable(j);
                if (variable instanceof ImplicitFunction) {
                    throw new CustomFunctionCalculationException(this, new JsclMessage(Messages.msg_13, MessageType.error, variable.getName()));
                }
            }
        }
    }

    @Override
    public int getMinParameters() {
        return parameterNames == null ? 0 : parameterNames.size();
    }

    @Override
    public int getMaxParameters() {
        return parameterNames == null ? Integer.MAX_VALUE: parameterNames.size();
    }

    @Override
    public Generic substitute(@Nonnull Variable variable, @Nonnull Generic generic) {
        return super.substitute(variable, generic);
    }

    @Override
    public Generic numeric() {
        return selfExpand().numeric();
    }

    @Override
    public Generic expand() {
        return selfExpand().expand();
    }

    @Override
    public Generic elementary() {
        return selfExpand().elementary();
    }

    @Override
    public Generic factorize() {
        return selfExpand().factorize();
    }

    @Override
    public Generic selfExpand() {
        Generic content = this.content;
        final List<ConstantData> parameterConstants = getParameterConstants();
        for (ConstantData cd : parameterConstants) {
            content = content.substitute(cd.local, cd.globalExpression);
        }
        for (int i = 0; i < parameterConstants.size(); i++) {
            final ConstantData cd = parameterConstants.get(i);
            content = content.substitute(cd.global, parameters[i]);
        }
        for (ConstantData cd : parameterConstants) {
            content = content.substitute(cd.global, cd.localExpression);
        }
        return content;
    }

    @Nonnull
    private List<ConstantData> getParameterConstants() {
        if(parameterConstants == null) {
            parameterConstants = makeParameterConstants(parameterNames);
        }
        return parameterConstants;
    }

    @Override
    public void copy(@Nonnull MathEntity mathEntity) {
        super.copy(mathEntity);
        if (mathEntity instanceof CustomFunction) {
            final CustomFunction that = (CustomFunction) mathEntity;
            this.content = that.content;
            this.parameterNames = new ArrayList<String>(that.parameterNames);
            this.description = that.description;
        }
    }

    @Override
    public Generic selfElementary() {
        throw new ArithmeticException();
    }

    @Override
    public Generic selfSimplify() {
        return expressionValue();
    }

    @Override
    public Generic selfNumeric() {
        throw new ArithmeticException();
    }

    @Override
    public Generic antiDerivative(@Nonnull Variable variable) throws NotIntegrableException {
        if (getParameterForAntiDerivation(variable) < 0) {
            throw new NotIntegrableException(this);
        } else {
            return this.content.antiDerivative(variable);
        }
    }

    @Override
    public Generic antiDerivative(int n) throws NotIntegrableException {
        throw new NotIntegrableException(this);
    }

    @Nonnull
    @Override
    public Generic derivative(@Nonnull Variable variable) {
        Generic result = JsclInteger.valueOf(0);

        for (int i = 0; i < parameters.length; i++) {
            // chain rule: f(x) = g(h(x)) => f'(x) = g'(h(x)) * h'(x)
            // hd = h'(x)
            // gd = g'(x)
            final Generic hd = parameters[i].derivative(variable);
            final Generic gd = this.content.derivative(variable);

            result = result.add(hd.multiply(gd));
        }

        return result;
    }

    @Override
    public Generic derivative(int n) {
        throw new ArithmeticException();
    }

    @Nonnull
    public String getContent() {
        return this.content.toString();
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    @Nonnull
    public List<String> getParameterNames() {
        return Collections.unmodifiableList(parameterNames);
    }

    @Nonnull
    @Override
    protected String formatUndefinedParameter(int i) {
        if (i < this.parameterNames.size()) {
            return parameterNames.get(i);
        } else {
            return super.formatUndefinedParameter(i);
        }
    }

    @Nonnull
    @Override
    public CustomFunction newInstance() {
        return new CustomFunction(name, parameterNames, content, description);
    }

    public static class Builder {

        private final boolean system;

        @Nonnull
        private String content;

        @Nullable
        private String description;

        @Nonnull
        private List<String> parameterNames;

        @Nonnull
        private String name;

        @Nullable
        private Integer id;

        public Builder(@Nonnull String name,
                       @Nonnull List<String> parameterNames,
                       @Nonnull String content) {
            this.system = false;
            this.content = content;
            this.parameterNames = parameterNames;
            this.name = name;
        }

        public Builder(@Nonnull IFunction function) {
            this.system = function.isSystem();
            this.content = function.getContent();
            this.description = function.getDescription();
            this.parameterNames = new ArrayList<String>(function.getParameterNames());
            this.name = function.getName();
            if (function.isIdDefined()) {
                this.id = function.getId();
            }
        }

        public Builder() {
            this.system = false;
        }

        public Builder(boolean system,
                       @Nonnull String name,
                       @Nonnull List<String> parameterNames,
                       @Nonnull String content) {
            this.system = system;
            this.content = content;
            this.parameterNames = parameterNames;
            this.name = name;
        }

        @Nonnull
        private static String prepareContent(@Nonnull String content) {
            final StringBuilder result = new StringBuilder(content.length());

            final char groupingSeparator = JsclMathEngine.getInstance().getGroupingSeparator();

            for (int i = 0; i < content.length(); i++) {
                final char ch = content.charAt(i);
                switch (ch) {
                    case ' ':
                    case '\'':
                    case '\n':
                    case '\r':
                        // do nothing
                        break;
                    default:
                        // remove grouping separator
                        if (ch != groupingSeparator) {
                            result.append(ch);
                        }
                }
            }

            return result.toString();
        }

        @Nonnull
        public Builder setDescription(@Nullable String description) {
            this.description = description;
            return this;
        }

        @Nonnull
        public Builder setId(@Nonnull Integer id) {
            this.id = id;
            return this;
        }

        @Nonnull
        public Builder setContent(@Nonnull String content) {
            this.content = content;
            return this;
        }

        @Nonnull
        public Builder setParameterNames(@Nonnull List<String> parameterNames) {
            this.parameterNames = parameterNames;
            return this;
        }

        @Nonnull
        public Builder setName(@Nonnull String name) {
            this.name = name;
            return this;
        }

        public CustomFunction create() throws CustomFunctionCalculationException {
            final CustomFunction customFunction = new CustomFunction(name, parameterNames, prepareContent(content), description);
            customFunction.setSystem(system);
            if (id != null) {
                customFunction.setId(id);
            }
            return customFunction;
        }
    }

    private final class ConstantData {
        @Nonnull
        final Constant global;
        @Nonnull
        final Constant local;
        @Nonnull
        final Generic globalExpression;
        @Nonnull
        final Generic localExpression;

        public ConstantData(@Nonnull String name) {
            global = new Constant(name + "#" + id);
            globalExpression = Expression.valueOf(global);
            local = new Constant(name);
            localExpression = Expression.valueOf(local);
        }
    }
}
