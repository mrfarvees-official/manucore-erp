package com.manucore.identity.domains;

import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Component
public class DnsTxtLookup {

    /** Returns the TXT values at the name, or an empty list when there are none. */
    public List<String> txt(String name) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        List<String> values = new ArrayList<>();
        DirContext context = new InitialDirContext(env);

        try {
            Attributes attributes = context.getAttributes(name, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");

            if (txt != null) {
                NamingEnumeration<?> all = txt.getAll();

                while (all.hasMore()) {
                    values.add(String.valueOf(all.next()).replace("\"", "").trim());
                }
            }
        } catch (NameNotFoundException none) {
            return List.of();
        } finally {
            context.close();
        }

        return values;
    }
}