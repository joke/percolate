package io.github.joke.percolate.docs.pathaccess;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface ContactMapper {

    // email: a JavaBean getter (getEmail()).
    @Map(target = "email", source = "contact.email")
    // areaCode: a getter (getPhone()) then a record accessor (areaCode()).
    @Map(target = "areaCode", source = "contact.phone.areaCode")
    // zip: a public field, no accessor method at all.
    @Map(target = "zip", source = "contact.zip")
    ContactView map(Contact contact);
}
// end::mapper[]

// tag::model[]
final class Contact {
    private final String email;
    private final Phone phone;
    public final String zip;

    Contact(String email, Phone phone, String zip) {
        this.email = email;
        this.phone = phone;
        this.zip = zip;
    }

    public String getEmail() {
        return email;
    }

    public Phone getPhone() {
        return phone;
    }
}

record Phone(String areaCode) {}

final class ContactView {
    private final String email;
    private final String areaCode;
    private final String zip;

    ContactView(String email, String areaCode, String zip) {
        this.email = email;
        this.areaCode = areaCode;
        this.zip = zip;
    }

    public String getEmail() {
        return email;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public String getZip() {
        return zip;
    }
}
// end::model[]
