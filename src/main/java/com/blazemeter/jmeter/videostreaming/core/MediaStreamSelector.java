package com.blazemeter.jmeter.videostreaming.core;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface MediaStreamSelector<T> {

  T findMatchingVariant(List<T> variants);

  default <V, U> T findSelectedVariant(Function<T, V> firstAttribute,
      BiPredicate<V, V> firstAttributeSelector, Function<T, U> secondAttribute,
      BiPredicate<U, U> secondAttributeSelector, List<T> variants) {
    T matchedVariant = findVariantPerAttribute(firstAttribute, firstAttributeSelector,
        variants);
    if (matchedVariant == null) {
      return null;
    }
    V selectedAttribute = firstAttribute.apply(matchedVariant);
    List<T> matchingVariants = variants.stream()
        .filter(v -> firstAttribute.apply(v).equals(selectedAttribute))
        .collect(Collectors.toList());
    return findVariantPerAttribute(secondAttribute, secondAttributeSelector, matchingVariants);
  }

  default <V> T findVariantPerAttribute(Function<T, V> attribute,
      BiPredicate<V, V> attributeSelector, List<T> variants) {
    V lastMatchAttribute = null;
    T lastMatchVariant = null;
    for (T variant : variants) {
      V attr = attribute.apply(variant);
      if (attributeSelector.test(attr, lastMatchAttribute)) {
        lastMatchAttribute = attr;
        lastMatchVariant = variant;
      }
    }
    return lastMatchVariant;
  }

}
