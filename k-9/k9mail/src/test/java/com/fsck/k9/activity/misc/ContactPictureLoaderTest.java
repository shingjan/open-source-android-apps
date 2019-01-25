package com.fsck.k9.activity.misc;


import com.fsck.k9.RobolectricTest;
import com.fsck.k9.mail.Address;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class ContactPictureLoaderTest extends RobolectricTest {

    @Test
    public void calcUnknownContactLetter_withNoNameUsesAddress() {
        Address address = new Address("<c@d.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("C", result);
    }

    @Test
    public void calcUnknownContactLetter_withAsciiName() {
        Address address = new Address("abcd <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("A", result);
    }

    @Test
    public void calcUnknownContactLetter_withLstroke() {
        Address address = new Address("Łatynka <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("Ł", result);
    }


    @Test
    public void calcUnknownContactLetter_withChinese() {
        Address address = new Address("千里之行﹐始于足下 <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("千", result);
    }

    @Test
    public void calcUnknownContactLetter_withCombinedGlyphs() {
        Address address = new Address("\u0061\u0300 <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("\u0041\u0300", result);
    }

    @Test
    public void calcUnknownContactLetter_withSurrogatePair() {
        Address address = new Address("\uD800\uDFB5 <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("\uD800\uDFB5", result);
    }

    @Test
    public void calcUnknownContactLetter_ignoresSpace() {
        Address address = new Address(" abcd <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("A", result);
    }


    @Test
    public void calcUnknownContactLetter_ignoresUsePunctuation() {
        Address address = new Address("-a <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("A", result);
    }

    @Test
    public void calcUnknownContactLetter_ignoresMatchEmoji() {
        Address address = new Address("\uD83D\uDE00 <a@b.com>");

        String result = ContactPictureLoader.calcUnknownContactLetter(address);

        assertEquals("?", result);
    }
}
