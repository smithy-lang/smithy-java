#!/bin/sh
set -eu
umask 077

cert_dir=${1:-certs}
mkdir -p "$cert_dir"
if [ -e "$cert_dir/server.pem" ] || [ -e "$cert_dir/server-key.pem" ]; then
    echo "Certificate or key already exists in $cert_dir; choose a new directory." >&2
    exit 1
fi

"${OPENSSL:-openssl}" req -x509 -newkey ec \
    -pkeyopt ec_paramgen_curve:prime256v1 -pkeyopt ec_param_enc:named_curve \
    -nodes -days 30 -subj '/CN=localhost' \
    -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1' \
    -addext 'basicConstraints=critical,CA:FALSE' \
    -addext 'keyUsage=critical,digitalSignature' \
    -addext 'extendedKeyUsage=serverAuth' \
    -keyout "$cert_dir/server-key.pem" -out "$cert_dir/server.pem"

echo "Created $cert_dir/server.pem and $cert_dir/server-key.pem"
