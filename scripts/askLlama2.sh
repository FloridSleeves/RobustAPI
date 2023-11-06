cd ..

GPU=$1

python askHF.py \
--ckpt_path meta-llama/Llama-2-7b-hf \
--tokenizer_path meta-llama/Llama-2-7b-hf \
--result_dir ./results/llama2/ \
--shot_number 1 \
--gpu $GPU
