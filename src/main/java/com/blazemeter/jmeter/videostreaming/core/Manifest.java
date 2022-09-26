package com.blazemeter.jmeter.videostreaming.core;

import java.net.URI;

public abstract class Manifest {
  protected final URI uri;

  protected Manifest(URI uri) {
    this.uri = uri;
  }

  protected abstract String getManifestType();
}
