package com.fsck.k9.crypto;


import com.fsck.k9.Identity;
import com.fsck.k9.helper.StringHelper;


public class OpenPgpApiHelper {

    /**
     * Create an "account name" from the supplied identity for use with the OpenPgp API's
     * <code>EXTRA_ACCOUNT_NAME</code>.
     *
     * @return A string with the following format:
     *         <code>display name &lt;user@example.com&gt;</code>
     *
     * @see org.openintents.openpgp.util.OpenPgpApi#EXTRA_ACCOUNT_NAME
     */
    public static String buildUserId(Identity identity) {
        StringBuilder sb = new StringBuilder();

        String name = identity.getName();
        if (!StringHelper.isNullOrEmpty(name)) {
            sb.append(name).append(" ");
        }
        sb.append("<").append(identity.getEmail()).append(">");

        return sb.toString();
    }
}
