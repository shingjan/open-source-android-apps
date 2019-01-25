/*****************************************************************************
 * tls.c
 *****************************************************************************
 * Copyright © 2004-2016 Rémi Denis-Courmont
 * $Id$
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

/**
 * @ingroup tls
 * @file
 * Transport Layer Session protocol API.
 */

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#ifdef HAVE_POLL
# include <poll.h>
#endif
#include <assert.h>
#include <errno.h>
#ifndef SOL_TCP
# define SOL_TCP IPPROTO_TCP
#endif

#include <vlc_common.h>
#include "libvlc.h"

#include <vlc_tls.h>
#include <vlc_modules.h>
#include <vlc_interrupt.h>

/*** TLS credentials ***/

static int tls_server_load(void *func, va_list ap)
{
    int (*activate) (vlc_tls_creds_t *, const char *, const char *) = func;
    vlc_tls_creds_t *crd = va_arg (ap, vlc_tls_creds_t *);
    const char *cert = va_arg (ap, const char *);
    const char *key = va_arg (ap, const char *);

    return activate (crd, cert, key);
}

static int tls_client_load(void *func, va_list ap)
{
    int (*activate) (vlc_tls_creds_t *) = func;
    vlc_tls_creds_t *crd = va_arg (ap, vlc_tls_creds_t *);

    return activate (crd);
}

static void tls_unload(void *func, va_list ap)
{
    void (*deactivate) (vlc_tls_creds_t *) = func;
    vlc_tls_creds_t *crd = va_arg (ap, vlc_tls_creds_t *);

    deactivate (crd);
}

vlc_tls_creds_t *
vlc_tls_ServerCreate (vlc_object_t *obj, const char *cert_path,
                      const char *key_path)
{
    vlc_tls_creds_t *srv = vlc_custom_create (obj, sizeof (*srv),
                                              "tls server");
    if (unlikely(srv == NULL))
        return NULL;

    if (key_path == NULL)
        key_path = cert_path;

    srv->module = vlc_module_load (srv, "tls server", NULL, false,
                                   tls_server_load, srv, cert_path, key_path);
    if (srv->module == NULL)
    {
        msg_Err (srv, "TLS server plugin not available");
        vlc_object_release (srv);
        return NULL;
    }

    return srv;
}

vlc_tls_creds_t *vlc_tls_ClientCreate (vlc_object_t *obj)
{
    vlc_tls_creds_t *crd = vlc_custom_create (obj, sizeof (*crd),
                                              "tls client");
    if (unlikely(crd == NULL))
        return NULL;

    crd->module = vlc_module_load (crd, "tls client", NULL, false,
                                   tls_client_load, crd);
    if (crd->module == NULL)
    {
        msg_Err (crd, "TLS client plugin not available");
        vlc_object_release (crd);
        return NULL;
    }

    return crd;
}

void vlc_tls_Delete (vlc_tls_creds_t *crd)
{
    if (crd == NULL)
        return;

    vlc_module_unload(crd, crd->module, tls_unload, crd);
    vlc_object_release (crd);
}


/*** TLS  session ***/

static vlc_tls_t *vlc_tls_SessionCreate(vlc_tls_creds_t *crd,
                                        vlc_tls_t *sock,
                                        const char *host,
                                        const char *const *alpn)
{
    vlc_tls_t *session;
    int canc = vlc_savecancel();
    session = crd->open(crd, sock, host, alpn);
    vlc_restorecancel(canc);
    if (session != NULL)
        session->p = sock;
    return session;
}

void vlc_tls_SessionDelete (vlc_tls_t *session)
{
    int canc = vlc_savecancel();
    session->close(session);
    vlc_restorecancel(canc);
}

static void cleanup_tls(void *data)
{
    vlc_tls_t *session = data;

    vlc_tls_SessionDelete (session);
}

vlc_tls_t *vlc_tls_ClientSessionCreate(vlc_tls_creds_t *crd, vlc_tls_t *sock,
                                       const char *host, const char *service,
                                       const char *const *alpn, char **alp)
{
    int val;

    vlc_tls_t *session = vlc_tls_SessionCreate(crd, sock, host, alpn);
    if (session == NULL)
        return NULL;

    int canc = vlc_savecancel();
    mtime_t deadline = mdate ();
    deadline += var_InheritInteger (crd, "ipv4-timeout") * 1000;

    struct pollfd ufd[1];
    ufd[0].fd = vlc_tls_GetFD(sock);

    vlc_cleanup_push (cleanup_tls, session);
    while ((val = crd->handshake(crd, session, host, service, alp)) != 0)
    {
        if (val < 0 || vlc_killed() )
        {
            if (val < 0)
                msg_Err(crd, "TLS session handshake error");
error:
            vlc_tls_SessionDelete (session);
            session = NULL;
            break;
        }

        mtime_t now = mdate ();
        if (now > deadline)
           now = deadline;

        assert (val <= 2);
        ufd[0] .events = (val == 1) ? POLLIN : POLLOUT;

        vlc_restorecancel(canc);
        val = vlc_poll_i11e(ufd, 1, (deadline - now) / 1000);
        canc = vlc_savecancel();
        if (val == 0)
        {
            msg_Err(crd, "TLS session handshake timeout");
            goto error;
        }
    }
    vlc_cleanup_pop();
    vlc_restorecancel(canc);
    return session;
}

vlc_tls_t *vlc_tls_ServerSessionCreate(vlc_tls_creds_t *crd,
                                       vlc_tls_t *sock,
                                       const char *const *alpn)
{
    return vlc_tls_SessionCreate(crd, sock, NULL, alpn);
}

vlc_tls_t *vlc_tls_SocketOpenTLS(vlc_tls_creds_t *creds, const char *name,
                                 unsigned port, const char *service,
                                 const char *const *alpn, char **alp)
{
    struct addrinfo hints =
    {
        .ai_socktype = SOCK_STREAM,
        .ai_protocol = IPPROTO_TCP,
    }, *res;

    msg_Dbg(creds, "resolving %s ...", name);

    int val = vlc_getaddrinfo_i11e(name, port, &hints, &res);
    if (val != 0)
    {   /* TODO: C locale for gai_strerror() */
        msg_Err(creds, "cannot resolve %s port %u: %s", name, port,
                gai_strerror(val));
        return NULL;
    }

    for (const struct addrinfo *p = res; p != NULL; p = p->ai_next)
    {
        vlc_tls_t *tcp = vlc_tls_SocketOpenAddrInfo(p, true);
        if (tcp == NULL)
        {
            msg_Err(creds, "socket error: %s", vlc_strerror_c(errno));
            continue;
        }

        vlc_tls_t *tls = vlc_tls_ClientSessionCreate(creds, tcp, name, service,
                                                     alpn, alp);
        if (tls != NULL)
        {   /* Success! */
            freeaddrinfo(res);
            return tls;
        }

        msg_Err(creds, "connection error: %s", vlc_strerror_c(errno));
        vlc_tls_SessionDelete(tcp);
    }

    /* Failure! */
    freeaddrinfo(res);
    return NULL;
}
