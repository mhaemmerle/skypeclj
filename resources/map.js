importPackage(java.net);
importPackage(java.io);

function loadUrl(urlString) {
    var url = new URL(urlString);
    var connection = url.openConnection();
    var br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    var sb = new java.lang.StringBuffer();

    var data;
    while((data = br.readLine()) != null) {
        sb.append(data);
    }

    br.close();

    return sb;
}

function aww(url) {
    var response = loadUrl(url);
    var result = JSON.parse(response);

    if(result.data.children.count <= 0) {
        return "Couldn't find anything cute...";
    }

    var urls = []
    for(var i = 0; i < result.data.children.length; i++) {
        var child = result.data.children[i];
        urls.push(child.data.url);
    }

    var rnd = Math.floor(Math.random() * urls.length)
    return urls[rnd];
}
