#!/usr/bin/python
# -*- coding: utf-8 -*-

import requests
import getpass
import os
import time
import sys

xml_data = """<?xml version="1.0" encoding="UTF-8"?><wps:Execute version="1.0.0" service="WPS" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.opengis.net/wps/1.0.0" xmlns:wfs="http://www.opengis.net/wfs" xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc" xmlns:wcs="http://www.opengis.net/wcs/1.1.1" xmlns:xlink="http://www.w3.org/1999/xlink" xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
  <ows:Identifier>groovy:nc_parse</ows:Identifier>
  <wps:DataInputs>
    <wps:Input>
      <ows:Identifier>processFileLimit</ows:Identifier>
      <wps:Data>
        <wps:LiteralData>20</wps:LiteralData>
      </wps:Data>
    </wps:Input>
  </wps:DataInputs>
  <wps:ResponseForm>
    <wps:RawDataOutput>
      <ows:Identifier>result</ows:Identifier>
    </wps:RawDataOutput>
  </wps:ResponseForm>
</wps:Execute>"""

xml_data2 = """<?xml version="1.0" encoding="UTF-8"?><wps:Execute version="1.0.0" service="WPS" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.opengis.net/wps/1.0.0" xmlns:wfs="http://www.opengis.net/wfs" xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc" xmlns:wcs="http://www.opengis.net/wcs/1.1.1" xmlns:xlink="http://www.w3.org/1999/xlink" xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
  <ows:Identifier>groovy:nc_process</ows:Identifier>
  <wps:DataInputs>
    <wps:Input>
      <ows:Identifier>locationPrecisionFilter</ows:Identifier>
      <wps:Data>
        <wps:LiteralData>20</wps:LiteralData>
      </wps:Data>
    </wps:Input>
    <wps:Input>
      <ows:Identifier>processTracksLimit</ows:Identifier>
      <wps:Data>
        <wps:LiteralData>20</wps:LiteralData>
      </wps:Data>
    </wps:Input>
  </wps:DataInputs>
  <wps:ResponseForm>
    <wps:RawDataOutput>
      <ows:Identifier>result</ows:Identifier>
    </wps:RawDataOutput>
  </wps:ResponseForm>
</wps:Execute>"""

#
# Watch changes in upload folder and trigger a WPS processing operation

def triggerParsing():
    resp = requests.post('http://localhost:8090/geoserver/wps',
                         data=xml_data)

    if resp.status_code != 200:

                # This means something went wrong.

        raise Networkerror(resp.status_code)
    else:
        print resp.content


def triggerProcess():
    resp = requests.post('http://localhost:8090/geoserver/wps',
                         data=xml_data2)

    if resp.status_code != 200:

                # This means something went wrong.

        raise Networkerror(resp.status_code)
    else:
        print resp.content


def main():
    while True:
        uploaded_files = [f for f in
                          os.listdir('/home/onomap/geoserver/data_dir/onomap_uploading'
                          ) if f.endswith('.zip')]
        if len(uploaded_files) > 0:
            print 'Find %d files run wps calls' % len(uploaded_files)
            try:
                triggerParsing()
                triggerProcess()
            except Networkerror, neterr:
                print 'Network error: {0}'.format(neterr)
            except Exception:
                print ('Unexpected error:', sys.exc_info()[0])
            print 'End of calls'

                # Wait 5 seconds

        time.sleep(5)


class Networkerror(RuntimeError):

    def __init__(self, arg):
        self.args = arg


main()
