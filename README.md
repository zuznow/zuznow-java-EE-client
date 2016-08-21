zuznow-java-EE-client
=================

In order to activate Zuznow Client you have to:
1. Installed filter in your Java EE application
2. Configure the client by editing your filter configuration

Configuring Zuznow Client:
domainID - The ID of the required app (from Zuznow dashboard)
APIKey - The API Key of the required app (from Zuznow dashboard)
originalDomain - the original app domain
apiServers - The URL of Zuznow Mobilization Server (ZMS)
	: for example - "http://proxy1.zuznow.com/API/"
exclude - url list separate by , 
	: this urls will not go via mobilization process and will be skipped in the filter 