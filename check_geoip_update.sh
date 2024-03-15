#!/bin/bash

info_br=release_info
json_key=geoipVer

git fetch origin ${info_br}
curr_geoip_ver=$(git show origin/${info_br}:version_info.json | jq -r .${json_key})

if [[ "$curr_geoip_ver" = "" || "$curr_geoip_ver" = "null" ]]; then
    curr_geoip_ver=0
fi

remote_geoip_ver=$(curl -s https://api.github.com/repos/Loyalsoldier/geoip/releases/latest | jq -r '.tag_name')

if (( remote_geoip_ver > curr_geoip_ver )); then
    exit 0
else
    exit 1
fi

