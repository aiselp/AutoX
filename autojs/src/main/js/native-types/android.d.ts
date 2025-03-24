namespace android {

    interface PackageInfo { }
    interface Context {
        startActivity: (intent: Intent) => void;
        sendBroadcast: (intent: Intent) => void;
        getPackageName(): string
    }
    interface IntentClass extends java.JavaClass {
        new(): Intent
        FLAG_ACTIVITY_NEW_TASK: number
        EXTRA_EMAIL: string
        EXTRA_CC: string
        EXTRA_BCC: string
        EXTRA_SUBJECT: string
        EXTRA_TEXT: string
        EXTRA_STREAM: string

        createChooser: (intent: Intent, title: string) => Intent
    }
    interface Uri extends java.javaObject { }
    interface Intent extends java.javaObject {
        package: string?;
        action: string?;
        data: Uri?;
        setClassName: (packageName: string, className: string) => this;
        putExtra: (name: string, value: any) => this;
        setType: (type: string?) => this;
        getType: () => string?;
        addFlags: (flag: number) => this;
        addCategory: (category: string) => this;
    }

}