# ngx-niceindex
Customizable directory indexes for nginx without index modules

A directory indexer for [nginx](http://nginx.org/) built to be simple, lightweight and easy to customize. It doesn't require indexer modules. It relies on xslt and css to generate clean html. It borrows heavily from [nginx-indexer](https://github.com/nervo/nginx-indexer),  [ngx-superbindex](https://github.com/gibatronic/ngx-superbindex) and [wilhelmy/dirlist.xslt](https://gist.github.com/wilhelmy/5a59b8eea26974a468c9)

## Requirements

[nginx](http://nginx.org/) with the [xslt](http://nginx.org/en/docs/http/ngx_http_xslt_module.html) module (included by default on Ubuntu packages).

## Caveats

* When nginx outputs autoindex data in xml, dates are GMT. There is no easy way to convert dates to local time in xslt1 and without additional packages.

## Usage

1. Clone the repository into a hidden .niceindex folder in the directory you want to list:

    ```git clone https://github.com/reven/ngx-niceindex.git .niceindex```

2. Add the following lines to the corresponding location in your `nginx.conf` file:
   ```nginx
   location / {
       autoindex on;
       autoindex_format xml;

       xslt_stylesheet /path/to/your/directory/.niceindex/niceindex.xslt path='$uri';
   }
   ```

3. Restart nginx with `systemctl restart nginx` and *voilà*!

![ngx-niceindex_screenshot](https://cloud.githubusercontent.com/assets/206993/22246884/df7c12e0-e1eb-11e6-96ba-8753d43583cd.png)
