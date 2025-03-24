

export interface IntentOptions {
    action?: string
    data?: string
    extras?: { [key: string]: any }
    className?: string
    packageName?: string
    flags?: (number | string)[]
    type?: string
    category?: string | string[]
}

export interface EmailOptions {
    /** 附件的路径。 */
    attachment?: string
    /** 密送收件人的邮件地址。如果有多个密送收件人，则用字符串数组表示 */
    bcc?: string | string[]
    /** 抄送收件人的邮件地址。如果有多个抄送收件人，则用字符串数组表示 */
    cc?: string | string[]
    /** 收件人的邮件地址。如果有多个收件人，则用字符串数组表示 */
    email?: string | string[]
    /** 邮件主题(标题) */
    subject?: string
    /** 邮件正文 */
    text?: string
}

/**
 * 此接口尚未支持
 */
export interface PMOptions {
    get: string[]
    match: string[]
}