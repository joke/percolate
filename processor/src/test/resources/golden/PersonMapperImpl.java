package test;

import java.lang.Override;

public final class PersonMapperImpl implements PersonMapper {
  @Override
  public TargetBean map(SourceBean source) {
    return new TargetBean(source.getFirstName(), source.getLastName());
  }
}
