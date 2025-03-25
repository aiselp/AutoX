

export function setGlobal(key: string, value: any) {
    (global as any)[key] = value;
}

export function setGlobalAnd$(key: string, value: any) {
    if (key.startsWith("$")) {
        key = key.substring(1);
    }
    setGlobal(key, value);
    setGlobal('$' + key, value);
}

export function exitIfError(action: () => void, defReturnValue?: any) {
    try {
        return action();
    } catch (err) {
        if (err instanceof java.lang.Throwable) {
            exit(err);
        } else if (err instanceof Error) {
            const e: any = err
            exit(new org.mozilla.javascript.EvaluatorException(err.name + ": " + err.message, e.fileName, e.lineNumber));
        } else {
            exit();
        }
        return defReturnValue;
    }
};