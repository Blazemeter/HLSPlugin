package com.blazemeter.jmeter.videostreaming.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Single owner of the playback state that must persist across slices within one controller
 * session, so a continuing session does not re-download the master/variant playlists (or manifest)
 * or rebuild the per-track playbacks on every slice.
 *
 * <p>Parameterized over the protocol-specific playback type {@code P} and top-level document type
 * {@code M} (HLS master playlist / DASH manifest) so a single class serves both protocols while the
 * owning sampler keeps full type safety.
 *
 * @param <P> protocol-specific media playback type
 * @param <M> protocol-specific top-level document type (manifest / master playlist)
 */
public class PlaybackSession<P, M> {

  private boolean initialized;
  private P primary;
  private final List<P> complements = new ArrayList<>();
  private M manifest;

  public boolean isInitialized() {
    return initialized;
  }

  public void setPrimary(P primary) {
    this.primary = primary;
  }

  public P getPrimary() {
    return primary;
  }

  public void setComplements(List<P> playbacks) {
    complements.clear();
    complements.addAll(playbacks);
  }

  public List<P> getComplements() {
    return complements;
  }

  public void setManifest(M manifest) {
    this.manifest = manifest;
  }

  public M getManifest() {
    return manifest;
  }

  /** Marks the session as usable for resume. Call only after setup fully succeeds. */
  public void markInitialized() {
    this.initialized = true;
  }

  /** Releases all retained playback state. Call on real end, error, interrupt or setup failure. */
  public void clear() {
    initialized = false;
    primary = null;
    complements.clear();
    manifest = null;
  }
}
