#! /bin/bash

# configure variables in the section below
GHDL_BACKEND="mcode"
GHDL_VERSION="0.35"
PLATFORM="ubuntu14"

GITHUB_SERVER="https://github.com"
GITHUB_SLUG="ghdl/ghdl"

TRAVIS_DIR="tmp"


# assemble the GitHub URL
GITHUB_TAGNAME="v$GHDL_VERSION"
GITHUB_RELEASE_FILE="ghdl-$GHDL_VERSION-$GHDL_BACKEND-$PLATFORM.tgz"
GITHUB_URL="$GITHUB_SERVER/$GITHUB_SLUG/releases/download/$GITHUB_TAGNAME/$GITHUB_RELEASE_FILE"


# other variables
GHDL_TARBALL="ghdl.tgz"

# define color escape codes
RED='\e[0;31m'			# Red
GREEN='\e[1;32m'		# Green
YELLOW='\e[1;33m'		# Yellow
MAGENTA='\e[1;35m'	# Magenta
CYAN='\e[1;36m'			# Cyan
NOCOLOR='\e[0m'			# No Color


echo -e "${MAGENTA}========================================${NOCOLOR}"
echo -e "${MAGENTA}     Downloading and installing GHDL    ${NOCOLOR}"
echo -e "${MAGENTA}========================================${NOCOLOR}"
echo -e "${CYAN}mkdir -p $TRAVIS_DIR${NOCOLOR}"
mkdir -p $TRAVIS_DIR
cd $TRAVIS_DIR

# downloading GHDL
echo -e "${CYAN}Downloading $GHDL_TARBALL from $GITHUB_URL...${NOCOLOR}"
wget -q $GITHUB_URL -O $GHDL_TARBALL
if [ $? -eq 0 ]; then
	echo -e "${GREEN}Download [SUCCESSFUL]${NOCOLOR}"
else
	echo 1>&2 -e "${RED}Download of $GITHUB_RELEASE_FILE [FAILED]${NOCOLOR}"
	exit 1
fi

# unpack GHDL
if [ -e $GHDL_TARBALL ]; then
	echo -e "${CYAN}Unpacking $GHDL_TARBALL... ${NOCOLOR}"
	tar -xzf $GHDL_TARBALL
	if [ $? -eq 0 ]; then
		echo -e "${GREEN}Unpack [SUCCESSFUL]${NOCOLOR}"
	else
		echo 1>&2 -e "${RED}Unpack [FAILED]${NOCOLOR}"
		exit 1
	fi
fi

# remove downloaded files
rm $GHDL_TARBALL

# gcc debugging
echo -e "${CYAN}Testing GCC version and configuration...${NOCOLOR}"
gcc -v

# test ghdl version
echo -e "${CYAN}Testing GHDL version...${NOCOLOR}"
./bin/ghdl -v
if [ $? -eq 0 ]; then
	echo -e "${GREEN}GHDL test [SUCCESSFUL]${NOCOLOR}"
else
	echo 1>&2 -e "${RED}GHDL test [FAILED]${NOCOLOR}"
	exit 1
fi

VERSION=20181129
REMOTE_TAR=https://github.com/ghdl/ghdl/archive/$VERSION.tar.gz

LOCAL_TAR=ghdl.tgz

wget $REMOTE_TAR -O $LOCAL_TAR

tar -xzf $LOCAL_TAR

rm $LOCAL_TAR

cd ghdl-$VERSION
mkdir build
cd build
../configure
make && make install


# Need to have gnat installed, and zlib1g-dev
#gnat version will depend on the Ubuntu version - 4.8 for 14, 5 for others?

# https://github.com/ghdl/ghdl/releases/download/v0.35/ghdl-0.35-mcode-ubuntu14.tgz

mkdir work
ghdl -i --workdir=work *.vhdl
ghdl -i --workdir=work */*.vhdl
ghdl -m --workdir=work system
