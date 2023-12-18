cd ..

python ./eval/evaluator.py \
--answer_dir=./results/deepseek-ins/one_shot/ \
--pattern_path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=deepseek \
--passk=1

rm .tmp*