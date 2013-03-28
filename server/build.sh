#!/bin/bash
dmd -L-lcurl `find src -iname "*.d"` `find src -iname "*.di"` -g -debug -o- -offlywithme
