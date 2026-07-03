package org.jabref.htmltonode.model;

/// A CSS length restricted to the units this library resolves.
/// `em`/`rem`/`%` are stored as [Unit#EM]; `px`/`pt` as [Unit#PX] (pt converted at 96/72).
///
/// @param value the numeric value, in the given unit
/// @param unit  the unit the value is expressed in
public record CssLength(double value, Unit unit) {

    /// The unit a [CssLength] is expressed in.
    public enum Unit {
        /// Absolute pixels.
        PX,
        /// Relative to the current font size (1 em = one font size).
        EM
    }

    /// Resolves this length to pixels.
    ///
    /// @param emSizePixels the current font size in pixels (used for [Unit#EM] values)
    /// @return the length in pixels
    public double toPixels(double emSizePixels) {
        return unit == Unit.PX ? value : value * emSizePixels;
    }
}
