package org.jabref.htmltonode.internal;

public enum WhiteSpaceMode {
    /// Collapse whitespace runs to single spaces (HTML default)
    NORMAL,
    /// Preserve whitespace and newlines (`pre`, `white-space: pre/pre-wrap/pre-line`)
    PRESERVE
}
