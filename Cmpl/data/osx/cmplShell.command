#!/bin/bash

oldPath=$PATH

CmplProgPath="`dirname \"$0\"`" 

if [[ $CmplProgPath = "." ]] ; then 
	CmplProgPath="$PWD/"
else 
	if [ ${0:0:1} = "/" ] ; then 
		CmplProgPath="$CmplProgPath/" 
	else
		CmplProgPath="$HOME/$CmplProgPath/" 
	fi
fi

export CmplProgPath

oldPath=$PATH 
PATH=$PATH:$CmplProgPath:${CmplProgPath}pyCmpl/scripts/Unix
export PATH

CMPLBINARY=${CmplProgPath}bin/cmpl
export CMPLBINARY
	
oldPS1=PS1 

case "$TERM" in
    xterm-color|*-256color) color_prompt=yes;;
esac

if [ "$color_prompt" = yes ]; then
	PS1="\[\033[01;32m\]cmplShell\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$" 
else
	PS1="cmplShell:\w\$"
fi
export PS1

if [ -d ~/Documents ] ; then
	cd ~/Documents
else
	cd
fi 

echo ""
echo "###########################################################################"
echo "# Welcome to CmplShell                                                    #" 
echo "# Run cmpl (pyCmpl) to solve a Cmpl (pyCmpl) problem.                     #" 
echo "# To start or stop CmplServer please use cmplServer <-start|-stop> [port] #"
echo "# Type exit to leave the Cmpl environment                                 #"
echo "###########################################################################"
echo ""
exec $BASH --norc


PATH=$oldPath 
export PATH
PS1=$oldPS1 
export PS1


