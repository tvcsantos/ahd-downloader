create-release:
	rm -rf release &&\
	cp -r release_template release &&\
	cp -r extra/. release