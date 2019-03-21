import requests
import getpass
import httplib

class APIError(Exception):
    pass
def main():	
	proxies = {
	  'http': 'http://137.121.61.4:3128',
	  'https': 'http://137.121.1.26:3128',
	}

	#user = raw_input("User ?")
	#password = getpass.getpass()
	resp = requests.get('https://onomap-gs.noise-planet.org/geoserver/rest',
	proxies=proxies)
	#data = open("wps/UploadZip.groovy").read(), auth=(user, password),
	#proxies=proxies)
	if resp.status_code != 200:
		# This means something went wrong.
		raise Exception(httplib.responses[resp.status_code])
	for todo_item in resp.json():
		print('{} {}'.format(todo_item['id'], todo_item['summary']))
main()