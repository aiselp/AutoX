

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