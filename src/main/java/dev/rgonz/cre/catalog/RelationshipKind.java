package dev.rgonz.cre.catalog;

/**
 * How a relationship group's source feature A relates to each target feature B.
 *
 * <ul>
 *   <li>{@code REQUIRES}: choosing A requires B.
 *   <li>{@code REQUIRED_WITH}: choosing B requires A. The wording keeps A as the subject while the
 *       requirement points the other way.
 *   <li>{@code NOT_ALLOWED_WITH}: A and B can't be chosen together, in either order.
 * </ul>
 */
public enum RelationshipKind {
  REQUIRES,
  REQUIRED_WITH,
  NOT_ALLOWED_WITH
}
