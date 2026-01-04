# ================= CONFIGURATION =================
# Your project root path (Please check this matches your actual path)
$projectPath = "D:\myFuzzProject" 

# The docker image name we committed earlier
$imageName = "my_fuzzer_image_fixed"

# Test duration in seconds (600s = 10 minutes)
$testDuration = 18000
# =================================================

# Define the 10 targets
$targets = @("T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08", "T09", "T10")

echo "=== Starting 10 Fuzzer Containers (Concurrent Run) ==="
echo "Project Path: $projectPath"
echo "Duration: $testDuration seconds"
echo "---------------------------------------------------"

foreach ($tid in $targets) {
    # 1. Stop and remove old container if it exists (suppress errors)
    docker rm -f "fuzz_${tid}" 2>$null
    
    echo "Launching target: $tid ..."

    # 2. Run the container
    # -d: Detached mode (background)
    # -v: Mount code and specific seeds
    # mvn exec: Run the Fuzzer class with arguments
    docker run -d `
        -v "${projectPath}:/app" `
        -v "${projectPath}\seeds_collection\${tid}:/app/fuzz-targets/init-seeds" `
        --name "fuzz_${tid}" `
        $imageName `
        bash -c "cd /app/fuzz-mut-demos/fuzzer-demo && mvn exec:java -Dexec.mainClass='edu.nju.isefuzz.Fuzzer' -Dexec.args='${tid} ${testDuration}' > /app/fuzz-mut-demos/fuzzer-demo/fuzz_${tid}.log 2>&1"
}

echo "---------------------------------------------------"
echo "=== All 10 containers launched successfully! ==="
echo "They are running in the background."
echo "1. Wait for 5 hours."
echo "2. Check logs at: ${projectPath}\fuzz-mut-demos\fuzzer-demo\fuzz_Txx.log"
echo "3. Check results at: ${projectPath}\fuzz-mut-demos\fuzzer-demo\output\Txx\trend.png"