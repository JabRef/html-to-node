/// The renderer-independent document model produced by parsing HTML.
///
/// A document is a list of [org.jabref.htmltonode.model.Block]s; paragraph-like blocks contain
/// [org.jabref.htmltonode.model.Inline] runs whose [org.jabref.htmltonode.model.InlineStyle]
/// is fully resolved (no inheritance left to compute). The model has no JavaFX dependencies,
/// so parsing can be tested and reused without a JavaFX runtime.
package org.jabref.htmltonode.model;
