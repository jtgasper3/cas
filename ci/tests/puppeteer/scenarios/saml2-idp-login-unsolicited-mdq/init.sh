#!/bin/bash
echo -e "Removing previous SAML metadata directory"
rm -Rf "${PWD}/ci/tests/puppeteer/scenarios/${SCENARIO}/saml-md"

echo -e "Fetching InCommon MDQ signing certificate..."
wget -P ${TMPDIR} http://md.incommon.org/certs/inc-md-cert-mdq.pem
