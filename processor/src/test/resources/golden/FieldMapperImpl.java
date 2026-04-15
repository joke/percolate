package test;

import java.lang.Override;

public final class FieldMapperImpl implements FieldMapper {
  @Override
  public FieldTarget map(FieldSource source) {
    FieldTarget target = new FieldTarget();
    target.name = source.name;
    return target;
  }
}
