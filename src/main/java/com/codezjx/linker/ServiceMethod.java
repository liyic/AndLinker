package com.codezjx.linker;

import android.os.RemoteException;
import android.util.Log;

import com.codezjx.linker.annotation.Callback;
import com.codezjx.linker.annotation.ClassName;
import com.codezjx.linker.annotation.MethodName;
import com.codezjx.linker.annotation.ParamName;
import com.codezjx.linker.model.Request;
import com.codezjx.linker.model.Response;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Created by codezjx on 2017/9/14.<br/>
 * Adapts an invocation of an interface method into an AIDL call.
 */
public class ServiceMethod {
    
    private static final String TAG = "ServiceMethod";

    private String mClassName;
    private String mMethodName;
    private ParameterHandler<?>[] mParameterHandlers;

    public ServiceMethod(Builder builder) {
        mClassName = builder.mClassName;
        mMethodName = builder.mMethodName;
        mParameterHandlers = builder.mParameterHandlers;
    }

    public Object invoke(ITransfer transfer, Object[] args) {
        @SuppressWarnings("unchecked")
        ParameterHandler<Object>[] handlers = (ParameterHandler<Object>[]) mParameterHandlers;

        int argumentCount = args != null ? args.length : 0;
        if (argumentCount != handlers.length) {
            throw new IllegalArgumentException("Argument count (" + argumentCount
                    + ") doesn't match expected count (" + handlers.length + ")");
        }

        for (int p = 0; p < argumentCount; p++) {
            handlers[p].apply(args, p);
        }

        Object result = null;
        try {
            Response response = transfer.execute(new Request(mClassName, mMethodName, args));
            result = response.getResult();
            Log.d(TAG, "Response from server, code:" + response.getStatusCode() + " msg:" + response.getStatusMessage());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static final class Builder {

        private Method mMethod;
        private Annotation[] mMethodAnnotations;
        private Annotation[][] mParameterAnnotationsArray;
        private Type[] mParameterTypes;

        private String mClassName = "";
        private String mMethodName = "";
        private ParameterHandler<?>[] mParameterHandlers;
        
        public Builder(Method method) {
            mMethod = method;
            mMethodAnnotations = method.getAnnotations();
            mParameterAnnotationsArray = method.getParameterAnnotations();
            mParameterTypes = method.getGenericParameterTypes();
        }
        
        public ServiceMethod build() {

            parseClassName(mMethod);

            for (Annotation annotation : mMethodAnnotations) {
                parseMethodAnnotation(annotation);
            }

            int parameterCount = mParameterAnnotationsArray.length;
            mParameterHandlers = new ParameterHandler<?>[parameterCount];
            for (int p = 0; p < parameterCount; p++) {
                Type parameterType = mParameterTypes[p];
                if (Utils.hasUnresolvableType(parameterType)) {
                    throw parameterError(p, "Parameter type must not include a type variable or wildcard: %s",
                            parameterType);
                }
                
                Annotation[] parameterAnnotations = mParameterAnnotationsArray[p];
                if (parameterAnnotations == null) {
                    throw parameterError(p, "No parameter annotation found.");
                }

                mParameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
            }
            
            return new ServiceMethod(this);
        }

        private void parseClassName(Method method) {
            ClassName className = method.getDeclaringClass().getAnnotation(ClassName.class);
            if (className != null) {
                mClassName = className.value();
            }
        }

        private void parseMethodAnnotation(Annotation annotation) {
            if (annotation instanceof MethodName) {
                mMethodName = ((MethodName) annotation).value();
            }
        }

        private ParameterHandler<?>  parseParameter(int p, Type parameterType, Annotation[] annotations) {
            Class<?> rawParameterType = Utils.getRawType(parameterType);
            for (Annotation annotation : annotations) {
                if (annotation instanceof ParamName) {
                    String paramName = ((ParamName) annotation).value();
                    return new ParameterHandler.ParamNameHandler<>(paramName, rawParameterType);
                } else if (annotation instanceof Callback) {
                    return new ParameterHandler.CallbackHandler<>(rawParameterType);
                }
            }
            throw parameterError(p, "No support annotation found.");
        }

        private RuntimeException methodError(String message, Object... args) {
            return methodError(null, message, args);
        }

        private RuntimeException methodError(Throwable cause, String message, Object... args) {
            message = String.format(message, args);
            return new IllegalArgumentException(message
                    + "\n    for method "
                    + mMethod.getDeclaringClass().getSimpleName()
                    + "."
                    + mMethod.getName(), cause);
        }

        private RuntimeException parameterError(
                Throwable cause, int p, String message, Object... args) {
            return methodError(cause, message + " (parameter #" + (p + 1) + ")", args);
        }

        private RuntimeException parameterError(int p, String message, Object... args) {
            return methodError(message + " (parameter #" + (p + 1) + ")", args);
        }

    }
    
}