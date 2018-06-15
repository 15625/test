setlocal

pushd %~dp0

set CLASSES=..\classes
set INPUT=input
set OUTPUT=output
set OUTPUT_EXPECTED=output.expected
set LIST=list

rmdir /s /q %OUTPUT%
if EXIST %OUTPUT% (
    echo %OUTPUT% folder still exists.
    goto :eof
)
mkdir %OUTPUT%

echo [Usage] >> %OUTPUT%\out.txt
echo [Usage] >> %OUTPUT%\err.txt
java -cp %CLASSES% Test >> %OUTPUT%\out.txt 2>> %OUTPUT%\err.txt

for /f %%a in (%LIST%) do (
    echo [%%a] >> %OUTPUT%\out.txt
    echo [%%a] >> %OUTPUT%\err.txt
    java -cp %CLASSES% Test %INPUT%\%%a >> %OUTPUT%\out.txt 2>> %OUTPUT%\err.txt
)

windiff %OUTPUT_EXPECTED% %OUTPUT%

popd

endlocal
