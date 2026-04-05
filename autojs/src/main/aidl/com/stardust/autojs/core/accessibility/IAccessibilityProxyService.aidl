package com.stardust.autojs.core.accessibility;

interface IAccessibilityProxyService {
    boolean isEnabled();
    boolean ensureEnabled();
    boolean disable();
}