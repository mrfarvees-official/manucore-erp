package com.manucore.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tenant {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 64)
    private String timezone;

    @Column(length = 3)
    private String currency;

    @Column(length = 35)
    private String locale;

    public Tenant(String id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    /* Columns are nullable so existing rows migrate; null falls back to a default. */
    public String getTimezone() {
        return timezone == null ? "UTC" : timezone;
    }

    public String getCurrency() {
        return currency == null ? "USD" : currency;
    }

    public String getLocale() {
        return locale == null ? "en-US" : locale;
    }
}