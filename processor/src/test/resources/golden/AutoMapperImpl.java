package test;

import java.lang.Override;

public final class AutoMapperImpl implements AutoMapper {
  @Override
  public AutoTarget map(AutoSource source) {
    return new AutoTarget(source.getName(), source.getAge());
  }
}
